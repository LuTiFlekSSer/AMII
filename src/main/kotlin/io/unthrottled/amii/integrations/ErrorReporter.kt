package io.unthrottled.amii.integrations

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.Consumer
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.Message
import io.sentry.protocol.User
import io.unthrottled.amii.config.Config
import io.unthrottled.amii.tools.runSafely
import java.awt.Component
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Properties
import java.util.stream.Collectors

class ErrorReporter : ErrorReportSubmitter() {
  companion object {
    private val log = Logger.getInstance(this::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()
  }

  override fun getReportActionText(): String = "Report Anonymously"

  override fun submit(
    events: Array<out IdeaLoggingEvent>,
    additionalInfo: String?,
    parentComponent: Component,
    consumer: Consumer<in SubmittedReportInfo>
  ): Boolean {
    ApplicationManager.getApplication()
      .executeOnPooledThread {
        Sentry.setUser(
          User().apply {
            this.id = Config.instance.userId
          }
        )
        runSafely({
          Sentry.init { options: SentryOptions ->
            options.dsn =
              RestClient.performGet(
                "https://jetbrains.assets.unthrottled.io/amii/sentry-dsn.txt"
              )
                .map { it.trim() }
                .orElse(
                  "https://9d45400dcf214fffb48f538e571781b4@o403546" +
                    ".ingest.sentry.io/5561788?maxmessagelength=50000"
                )
          }
        }) {
          log.warn("Unable to set up sentry for raisins.", it)
        }

        events.forEach {
          Sentry.captureEvent(
            addSystemInfo(
              SentryEvent()
                .apply {
                  this.level = SentryLevel.ERROR
                  this.serverName = getAppName().second
                  this.setExtra("Additional Info", additionalInfo ?: "None")
                }
            ).apply {
              this.message = Message().apply {
                this.message = it.throwableText
              }
            }
          )
        }
        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
      }
    return true
  }

  private fun addSystemInfo(event: SentryEvent): SentryEvent {
    val pair = getAppName()
    val appInfo = pair.first
    val appName = pair.second
    val properties = System.getProperties()
    return event.apply {
      setExtra("App Name", appName)
      setExtra("Build Info", getBuildInfo(appInfo))
      setExtra("JRE", getJRE(properties))
      setExtra("VM", getVM(properties))
      setExtra(
        "System Info",
        listOf(
          properties.getProperty("os.name", "unknown"),
          properties.getProperty("os.version", ""),
          properties.getProperty("os.arch", "")
        ).filter(String::isNotBlank).joinToString(" ")
      )
      setExtra("GC", getGC())
      setExtra("Memory", Runtime.getRuntime().maxMemory() / FileUtilRt.MEGABYTE)
      setExtra("Cores", Runtime.getRuntime().availableProcessors())
      setExtra("Plugin Config", gson.toJson(Config.instance))
    }
  }

  private fun getJRE(properties: Properties): String {
    val javaVersion = properties.getProperty(
      "java.runtime.version",
      properties.getProperty("java.version", "unknown")
    )
    val arch = properties.getProperty("os.arch", "")
    return listOf(javaVersion, arch).filter(String::isNotBlank).joinToString(" ")
  }

  private fun getVM(properties: Properties): String {
    val vmVersion = properties.getProperty("java.vm.name", "unknown")
    val vmVendor = properties.getProperty("java.vendor", "unknown")
    return "$vmVersion ($vmVendor)"
  }

  private fun getGC() = ManagementFactory.getGarbageCollectorMXBeans().stream()
    .map { it.name }.collect(Collectors.joining(","))

  private fun getBuildInfo(appInfo: ApplicationInfo): String {
    val cal = appInfo.buildDate
    val dateFormat = if (appInfo.build.isSnapshot) {
      SimpleDateFormat("yyyy-MM-dd HH:mm")
    } else {
      SimpleDateFormat("yyyy-MM-dd")
    }
    return "${appInfo.build.asString()} (${dateFormat.format(cal.time)})"
  }

  private fun getAppName(): Pair<ApplicationInfo, String> {
    val appInfo = ApplicationInfo.getInstance()
    var appName = appInfo.fullApplicationName
    val edition = ApplicationNamesInfo.getInstance().editionName
    if (edition != null) appName += " ($edition)"
    return Pair(appInfo, appName)
  }
}
