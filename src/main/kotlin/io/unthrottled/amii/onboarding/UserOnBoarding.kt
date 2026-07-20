package io.unthrottled.amii.onboarding

import com.intellij.openapi.project.Project
import io.unthrottled.amii.config.Config
import io.unthrottled.amii.promotion.PromotionManager
import io.unthrottled.amii.services.backgroundTasks
import io.unthrottled.amii.tools.toOptional
import java.util.Optional
import java.util.UUID

object UserOnBoarding {

  fun attemptToPerformNewUpdateActions(project: Project): Boolean {
    val installedVersion = getVersion()
    val updatedVersion = installedVersion.filter { it != Config.instance.version }
    updatedVersion.ifPresent { newVersion ->
      Config.instance.version = newVersion
      UpdateNotification.display(project, newVersion)
    }

    val isNewUser = Config.instance.userId.isEmpty()
    if (isNewUser) {
      Config.instance.userId = UUID.randomUUID().toString()
    }

    installedVersion.ifPresent { version ->
      project.backgroundTasks().launch("AMII plugin promotion check") {
        PromotionManager.registerPromotion(version, isNewUser = isNewUser)
      }
    }

    return updatedVersion.isPresent
  }

  fun getVersion(): Optional<String> =
    UserOnBoarding::class.java.getResourceAsStream("/amii-version.txt")
      .toOptional()
      .map { versionStream ->
        versionStream.bufferedReader().use { it.readText().trim() }
      }
      .filter(String::isNotEmpty)
}
