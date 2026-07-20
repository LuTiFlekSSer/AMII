package io.unthrottled.amii.onboarding

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import icons.AMIIIcons.PLUGIN_ICON
import io.unthrottled.amii.assets.MemeAssetCategory
import io.unthrottled.amii.assets.VisualAssetDefinitionService
import io.unthrottled.amii.config.Constants.PLUGIN_NAME
import io.unthrottled.amii.services.backgroundTasks
import org.intellij.lang.annotations.Language

@Suppress("MaxLineLength")
@Language("HTML")
private fun buildUpdateMessage(updateAsset: String?): String {
  val updateImage = updateAsset?.let {
    """<img alt='Thanks for downloading!' src="$it" width='256'><br/><br/><br/>"""
  }.orEmpty()
  return """
      What's New?<br>
      <ul>
        <li>Added 2026.1 and 2026.2 IDE support</li>
        <li>Fixed an intermittent IDE startup freeze</li>
      </ul>
      <br>See the <a href="https://github.com/ani-memes/AMII#documentation">documentation</a> for features, usages, and configurations.
      <br>The <a href="https://github.com/ani-memes/AMII/blob/master/CHANGELOG.md">changelog</a> is available for more details.
      <br>Welcome <a href='https://plugins.jetbrains.com/plugin/13381-waifu-motivator'>Waifu Motivator</a> users! ❤️
      <br><br>
      <div style='text-align: center'>$updateImage
      Thanks for downloading!
      </div>
  """.trimIndent()
}

object UpdateNotification {

  private const val UPDATE_CHANNEL_NAME = "$PLUGIN_NAME Updates"
  private val notificationGroup
    get() = NotificationGroupManager.getInstance()
      .getNotificationGroup(UPDATE_CHANNEL_NAME)

  fun display(
    project: Project,
    newVersion: String
  ) {
    project.backgroundTasks().launch("AMII update notification asset") {
      val updateAsset = resolveUpdateAsset()
      ApplicationManager.getApplication().invokeLater({
        if (project.isDisposed.not()) {
          displayPrepared(project, newVersion, updateAsset)
        }
      }, ModalityState.any())
    }
  }

  private fun resolveUpdateAsset(): String? =
    runCatching {
      VisualAssetDefinitionService.getRandomAssetByCategory(
        MemeAssetCategory.HAPPY
      ).map { it.filePath.toString() }.orElse(null)
    }.getOrNull()

  private fun displayPrepared(
    project: Project,
    newVersion: String,
    updateAsset: String?
  ) {
    val updateNotification = notificationGroup.createNotification(
      buildUpdateMessage(updateAsset),
      NotificationType.INFORMATION
    )
      .setTitle("$PLUGIN_NAME updated to v$newVersion")
      .setIcon(PLUGIN_ICON)
      .setListener(NotificationListener.UrlOpeningListener(false))

    updateNotification.notify(project)
  }

  fun sendMessage(
    title: String,
    message: String,
    project: Project? = null
  ) {
    showRegularNotification(
      title,
      message,
      project = project,
      listener = defaultListener
    )
  }

  private val defaultListener = NotificationListener.UrlOpeningListener(false)

  private fun showRegularNotification(
    title: String = "",
    content: String,
    project: Project? = null,
    listener: NotificationListener? = defaultListener
  ) {
    notificationGroup.createNotification(
      content,
      NotificationType.INFORMATION
    ).setIcon(PLUGIN_ICON)
      .setTitle(title)
      .setListener(listener ?: defaultListener)
      .notify(project)
  }

}
