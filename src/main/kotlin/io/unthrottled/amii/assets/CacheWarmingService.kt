package io.unthrottled.amii.assets

import com.intellij.ide.IdleTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import io.unthrottled.amii.config.Config
import io.unthrottled.amii.platform.UpdateAssetsListener
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

@OptIn(FlowPreview::class)
class CacheWarmingService(
  private val coroutineScope: CoroutineScope
) : Disposable, Runnable {

  companion object {
    val instance: CacheWarmingService
      get() = ApplicationManager.getApplication().getService(CacheWarmingService::class.java)
  }

  private var dateRequested = Instant.EPOCH

  private val idleListenerJob = coroutineScope.launch(CoroutineName("AMII cache warming idle listener")) {
    IdleTracker.getInstance().events
      .debounce(Config.DEFAULT_IDLE_TIMEOUT_IN_MINUTES.minutes)
      .collect {
        withContext(Dispatchers.EDT) { run() }
      }
  }

  fun init() {
    // empty so that this registers the idle listener.
  }

  override fun dispose() {
    idleListenerJob.cancel()
  }

  override fun run() {
    val meow = Instant.now()
    if (Duration.between(dateRequested, meow).toDays() < 1) return

    dateRequested = meow

    ApplicationManager.getApplication().messageBus.syncPublisher(
      UpdateAssetsListener.TOPIC
    ).onRequestedBackgroundUpdate()
  }
}
