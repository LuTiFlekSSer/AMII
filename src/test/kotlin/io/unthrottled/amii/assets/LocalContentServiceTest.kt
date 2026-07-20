package io.unthrottled.amii.assets

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant

class LocalContentServiceTest {

  @Before
  fun setUp() {
    mockkObject(AssetCheckService)
  }

  @After
  fun tearDown() {
    unmockkObject(AssetCheckService)
  }

  @Test
  fun `stale metadata does not mark a network check as successful`() {
    val localAsset = Files.createTempFile("amii-stale-metadata", ".json")
    val lastSuccessfulCheck = Instant.parse("2026-04-03T08:56:34Z")
    try {
      every { AssetCheckService.hasBeenCheckedToday(localAsset) } returns false
      every { AssetCheckService.getCheckedDate(localAsset) } returns lastSuccessfulCheck

      val result = LocalContentService.hasAPIAssetChanged(localAsset)

      assertThat(result.status).isEqualTo(AssetStatus.STALE)
      assertThat(result.metaData).isEqualTo(lastSuccessfulCheck)
      verify(exactly = 0) { AssetCheckService.writeCheckedDate(any<Path>()) }
    } finally {
      Files.deleteIfExists(localAsset)
    }
  }

  @Test
  fun `legacy check timestamp well after metadata write forces full refresh`() {
    val localAsset = Files.createTempFile("amii-legacy-false-success", ".json")
    val checkedDate = Instant.parse("2026-04-03T11:56:33Z")
    try {
      Files.setLastModifiedTime(
        localAsset,
        FileTime.from(checkedDate.minus(Duration.ofMinutes(6)))
      )
      every { AssetCheckService.getCheckedDate(localAsset) } returns checkedDate
      every { AssetCheckService.hasBeenCheckedToday(localAsset) } returns true

      val result = LocalContentService.hasAPIAssetChanged(localAsset)

      assertThat(result.status).isEqualTo(AssetStatus.STALE)
      assertThat(result.metaData).isNull()
    } finally {
      Files.deleteIfExists(localAsset)
    }
  }

  @Test
  fun `normal check timestamp shortly after metadata write remains current`() {
    val localAsset = Files.createTempFile("amii-normal-success", ".json")
    val checkedDate = Instant.parse("2026-04-03T11:56:33Z")
    try {
      Files.setLastModifiedTime(
        localAsset,
        FileTime.from(checkedDate.minus(Duration.ofMinutes(4)))
      )
      every { AssetCheckService.getCheckedDate(localAsset) } returns checkedDate
      every { AssetCheckService.hasBeenCheckedToday(localAsset) } returns true

      val result = LocalContentService.hasAPIAssetChanged(localAsset)

      assertThat(result.status).isEqualTo(AssetStatus.CURRENT)
      assertThat(result.metaData).isNull()
    } finally {
      Files.deleteIfExists(localAsset)
    }
  }
}
