package io.unthrottled.amii.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.unthrottled.amii.events.EVENT_TOPIC
import io.unthrottled.amii.events.UserEvent
import io.unthrottled.amii.events.UserEventCategory
import io.unthrottled.amii.events.UserEvents
import io.unthrottled.amii.tools.PluginMessageBundle

object WelcomeService {

  fun greetUser(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        project.messageBus
          .syncPublisher(EVENT_TOPIC)
          .onDispatch(
            UserEvent(
              UserEvents.STARTUP,
              UserEventCategory.POSITIVE,
              PluginMessageBundle.message("user.event.startup.name"),
              project
            )
          )
      }
    }
  }
}
