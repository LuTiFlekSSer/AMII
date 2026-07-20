package io.unthrottled.amii.memes.player

import io.unthrottled.amii.assets.AudibleContent
import io.unthrottled.amii.config.Config
import io.unthrottled.amii.tools.runSafelyWithResult
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10

class ClipSoundPlayer(
  private val audibleAssetContent: AudibleContent
) : MemePlayer {

  companion object {
    private const val MILLS_DIVISOR = 1000
    private const val DECIBEL_MULTIPLICAND = 20F
    private const val MINIMUM_VOLUME = 0.0001F
  }

  @Suppress("MagicNumber")
  @Volatile
  private var clip: Clip? = null

  @Volatile
  private var stopped = false

  override val duration: Long
    get() = clip?.microsecondLength?.div(MILLS_DIVISOR) ?: -1L

  override fun prepare() {
    getOrCreateClip()
  }

  override fun play() {
    val clipToPlay = getOrCreateClip() ?: return
    if (stopped) {
      clipToPlay.close()
    } else {
      clipToPlay.start()
    }
  }

  @Synchronized
  override fun stop() {
    stopped = true
    clip?.close()
    clip = null
  }

  override fun clone(): MemePlayer =
    ClipSoundPlayer(audibleAssetContent)

  private fun getOrCreateClip(): Clip? {
    if (stopped) return null
    clip?.let { return it }

    // AudioSystem/open may block on a mixer or file. Do it outside the monitor
    // so an EDT-triggered dismissal can never wait on audio initialization.
    val newClip = createClip() ?: return null
    synchronized(this) {
      if (stopped) {
        newClip.close()
        return null
      }
      clip?.let { existingClip ->
        newClip.close()
        return existingClip
      }
      configureGain(newClip)
      clip = newClip
      return newClip
    }
  }

  private fun createClip(): Clip? =
    runSafelyWithResult({
      AudioSystem.getAudioInputStream(audibleAssetContent.filePath.toURL()).use { inputStream ->
        val newClip = createSystemClip() ?: return@use null
        newClip.open(inputStream)
        newClip.addLineListener {
          if (it.type == LineEvent.Type.STOP) {
            newClip.close()
          }
        }
        newClip
      }
    }) {
      null
    }

  @Suppress("MagicNumber")
  private fun createSystemClip(): Clip? =
    runSafelyWithResult({
      AudioSystem.getClip()
    }) {
      runSafelyWithResult({
        val format = AudioFormat(
          AudioFormat.Encoding.PCM_SIGNED,
          44100F,
          16,
          2,
          4,
          AudioSystem.NOT_SPECIFIED.toFloat(),
          true
        )
        val info = DataLine.Info(Clip::class.java, format)
        AudioSystem.getLine(info) as Clip
      }) {
        null
      }
    }

  private fun configureGain(newClip: Clip) {
    if (newClip.isControlSupported(FloatControl.Type.MASTER_GAIN).not()) return
    val gainControl = newClip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
    val requestedGain = DECIBEL_MULTIPLICAND * log10(Config.instance.volume.coerceAtLeast(MINIMUM_VOLUME))
    gainControl.value = requestedGain.coerceIn(gainControl.minimum, gainControl.maximum)
  }
}
