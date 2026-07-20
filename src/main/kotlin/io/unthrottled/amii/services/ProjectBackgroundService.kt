package io.unthrottled.amii.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs blocking plugin work outside the IDE startup and UI threads.
 * The injected scope is cancelled when the project closes or the plugin unloads.
 */
@Service(Service.Level.PROJECT)
class ProjectBackgroundService(
  private val coroutineScope: CoroutineScope
) {
  fun launch(taskName: String, task: () -> Unit): Job =
    coroutineScope.launch(Dispatchers.IO + CoroutineName(taskName)) {
      task()
    }
}

fun Project.backgroundTasks(): ProjectBackgroundService =
  getService(ProjectBackgroundService::class.java)
