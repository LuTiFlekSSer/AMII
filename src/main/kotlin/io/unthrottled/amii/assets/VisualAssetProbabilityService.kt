package io.unthrottled.amii.assets

import com.intellij.ide.IdleTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import io.unthrottled.amii.config.Config
import io.unthrottled.amii.memes.MemeDisplayListener
import io.unthrottled.amii.tools.ProbabilityTools
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Optional
import kotlin.time.Duration.Companion.minutes
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

@OptIn(FlowPreview::class)
class VisualAssetProbabilityService(
  private val coroutineScope: CoroutineScope
) : Disposable, MemeDisplayListener, Runnable {

  companion object {
    val instance: VisualAssetProbabilityService
      get() = ApplicationManager.getApplication().getService(VisualAssetProbabilityService::class.java)
  }

  private val messageBusConnection = ApplicationManager.getApplication().messageBus.connect()

  private val seenAssetLedger = AssetObservationService.getInitialItem()

  private val random = java.util.Random()
  private val probabilityTools = ProbabilityTools(
    Random(System.currentTimeMillis())
  )

  private val idleListenerJob = coroutineScope.launch(CoroutineName("AMII asset ledger idle listener")) {
    IdleTracker.getInstance().events
      .debounce(
        Config.instance.idleTimeoutInMinutes
          .coerceIn(1L, Int.MAX_VALUE.toLong())
          .minutes
      )
      .collect {
        withContext(Dispatchers.EDT) { run() }
      }
  }

  init {
    messageBusConnection.subscribe(MemeDisplayListener.TOPIC, this)
  }

  fun pickAssetFromList(visualMemes: Collection<VisualAssetEntity>): Optional<VisualAssetEntity> {
    val seenTimes = visualMemes.map { getSeenCount(it) }
    val maxSeen = seenTimes.stream().mapToInt { it }.max().orElse(0)
    val totalItems = visualMemes.size
    return probabilityTools.pickFromWeightedList(
      visualMemes.map {
        val timesObserved = getSeenCount(it)
        it to 1 + (
          (
            abs(random.nextGaussian()) *
              safelyScale(totalItems, maxSeen - timesObserved)
            )
          ).toLong()
      }.shuffled(random)
    )
  }

  // small brain fix to prevent
  // large discrepancies in seen assets having a big difference
  // in seen times. (Avoids overflows)
  private fun safelyScale(totalItems: Int, i: Int): Double {
    var runningTotal: Long = totalItems.toLong()
    var powerLeft = i
    while (powerLeft > 0) {
      val nextVal: Long = runningTotal * totalItems
      if (nextVal < runningTotal) {
        return runningTotal.toDouble()
      }
      powerLeft--
      runningTotal = nextVal
    }

    return runningTotal.toDouble()
  }

  // give strong bias to assets that haven't
  // been seen by the user during the ledger cycle duration see:
  // <code>MAX_ALLOWED_DAYS_PERSISTED</code> in AssetObservationService for
  // more details
  private fun getSeenCount(it: VisualAssetEntity) =
    seenAssetLedger.assetSeenCounts.getOrDefault(it.id, 0)

  override fun dispose() {
    messageBusConnection.dispose()
    AssetObservationService.persistLedger(seenAssetLedger)
    idleListenerJob.cancel()
  }

  override fun onDisplay(visualMemeId: String) {
    seenAssetLedger.assetSeenCounts[visualMemeId] =
      getAssetSeenCount(visualMemeId) + 1
  }

  // This prevents newly seen assets from always being
  // biased to be shown to the user.
  private fun getAssetSeenCount(visualMemeId: String): Int {
    val seenAssets = seenAssetLedger.assetSeenCounts
    return if (seenAssets.containsKey(visualMemeId)) {
      seenAssets[visualMemeId]!!
    } else {
      floor(seenAssets.entries.map { it.value }.average()).toInt()
    }
  }

  override fun run() {
    val updatedLedger = AssetObservationService.persistLedger(seenAssetLedger)
    updatedLedger.assetSeenCounts.forEach { (assetId, assetSeenCount) ->
      seenAssetLedger.assetSeenCounts[assetId] = assetSeenCount
    }
  }
}
