package io.unthrottled.amii.memes.player

import io.unthrottled.amii.assets.AudibleContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files

class ClipSoundPlayerTest {

  @Test
  fun `constructing a player does not open the audio file`() {
    val temporaryDirectory = Files.createTempDirectory("amii-lazy-audio")
    try {
      val missingAudio = temporaryDirectory.resolve("missing.wav").toUri()

      val player = ClipSoundPlayer(AudibleContent(missingAudio))

      assertThat(player.duration).isEqualTo(MemePlayer.NO_LENGTH)
    } finally {
      Files.deleteIfExists(temporaryDirectory)
    }
  }

  @Test
  fun `preparing a missing audio file fails without leaking an exception`() {
    val temporaryDirectory = Files.createTempDirectory("amii-prepare-audio")
    try {
      val missingAudio = temporaryDirectory.resolve("missing.wav").toUri()
      val player = ClipSoundPlayer(AudibleContent(missingAudio))

      player.prepare()

      assertThat(player.duration).isEqualTo(MemePlayer.NO_LENGTH)
    } finally {
      Files.deleteIfExists(temporaryDirectory)
    }
  }
}
