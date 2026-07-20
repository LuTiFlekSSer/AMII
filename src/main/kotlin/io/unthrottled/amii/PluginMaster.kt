package io.unthrottled.amii

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.unthrottled.amii.assets.AnimeContentManager
import io.unthrottled.amii.assets.AudibleContentManager
import io.unthrottled.amii.assets.CacheWarmingService
import io.unthrottled.amii.assets.CharacterContentManager
import io.unthrottled.amii.assets.LocalVisualContentManager
import io.unthrottled.amii.assets.RemoteVisualContentManager
import io.unthrottled.amii.assets.Status
import io.unthrottled.amii.assets.VisualEntityRepository
import io.unthrottled.amii.listeners.IdleEventListener
import io.unthrottled.amii.listeners.SilenceListener
import io.unthrottled.amii.onboarding.UpdateNotification
import io.unthrottled.amii.onboarding.UserOnBoarding
import io.unthrottled.amii.platform.LifeCycleManager
import io.unthrottled.amii.platform.UpdateAssetsListener
import io.unthrottled.amii.services.WelcomeService
import io.unthrottled.amii.services.backgroundTasks
import io.unthrottled.amii.tools.Logging
import io.unthrottled.amii.tools.PluginMessageBundle
import io.unthrottled.amii.tools.logger
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream

class PluginMaster : Disposable, Logging {

  companion object {
    val instance: PluginMaster
      get() = ApplicationManager.getApplication().getService(PluginMaster::class.java)
  }

  private val projectListeners: ConcurrentMap<String, ProjectListeners> = ConcurrentHashMap()
  private val assetHealthCheckInProgress = AtomicBoolean(false)
  private val fullAssetUpdateRequested = AtomicBoolean(false)

  init {
    CacheWarmingService.instance.init()
    LocalVisualContentManager.init()
  }

  fun projectOpened(project: Project) {
    registerListenersForProject(project)
  }

  private fun registerListenersForProject(project: Project) {
    if (UserOnBoarding.attemptToPerformNewUpdateActions(project)) {
      fullAssetUpdateRequested.set(true)
    }
    val projectId = project.locationHash
    if (projectListeners.containsKey(projectId).not()) {
      WelcomeService.greetUser(project)
      projectListeners[projectId] =
        ProjectListeners(project)
      scheduleAssetHealthCheck(project)
    }
  }

  private fun scheduleAssetHealthCheck(project: Project) {
    if (assetHealthCheckInProgress.compareAndSet(false, true).not()) return

    val healthCheckJob = try {
      project.backgroundTasks().launch("AMII asset health check") {
        try {
          checkIfInGoodState(project)
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (error: Throwable) {
          logger().warn("Unable to initialize AMII assets in the background.", error)
        }
      }
    } catch (error: Throwable) {
      assetHealthCheckInProgress.set(false)
      if (!project.isDisposed) {
        logger().warn("Unable to schedule AMII asset initialization.", error)
      }
      return
    }

    healthCheckJob.invokeOnCompletion {
      assetHealthCheckInProgress.set(false)
    }
  }

  private fun checkIfInGoodState(project: Project) {
    // This service initializes Anime, Character and Visual managers in one
    // deterministic sequence, preventing cross-manager class-init races.
    VisualEntityRepository.instance
    if (fullAssetUpdateRequested.compareAndSet(true, false)) {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(UpdateAssetsListener.TOPIC)
        .onRequestedUpdate()
    }
    val isInGoodState = Stream.of(
      AudibleContentManager,
      RemoteVisualContentManager,
      AnimeContentManager,
      CharacterContentManager
    ).map { it.status }
      .allMatch { it == Status.OK }
    if (!isInGoodState && !project.isDisposed) {
      UpdateNotification.sendMessage(
        PluginMessageBundle.message("notifications.bad.state.title"),
        PluginMessageBundle.message("notifications.bad.state.body"),
        project
      )
    }
  }

  fun projectClosed(project: Project) {
    projectListeners[project.locationHash]?.dispose()
    projectListeners.remove(project.locationHash)
  }

  override fun dispose() {
    projectListeners.forEach { (_, listeners) -> listeners.dispose() }
    LifeCycleManager.dispose()
  }

  fun onUpdate() {
    ProjectManager.getInstance().openProjects
      .forEach { registerListenersForProject(it) }
  }
}

internal data class ProjectListeners(
  private val project: Project
) : Disposable {

  private val idleEventListener = IdleEventListener(project)
  private val silenceListener = SilenceListener(project)

  override fun dispose() {
    idleEventListener.dispose()
    silenceListener.dispose()
  }
}
