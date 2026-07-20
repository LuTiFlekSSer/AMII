package io.unthrottled.amii.assets

import io.unthrottled.amii.assets.AssetCheckService.getCheckedDate
import io.unthrottled.amii.assets.AssetCheckService.hasBeenCheckedToday
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

enum class AssetStatus {
  NOT_DOWNLOADED, STALE, CURRENT
}

data class AssetCheckPayload(
  val status: AssetStatus,
  val metaData: Any? = null
)

object LocalContentService {
  private val LEGACY_FALSE_SUCCESS_TOLERANCE = Duration.ofMinutes(5)

  fun hasAssetChanged(
    localInstallPath: Path
  ): Boolean =
    !Files.exists(localInstallPath)

  fun hasAPIAssetChanged(
    localInstallPath: Path
  ): AssetCheckPayload {
    if (!Files.exists(localInstallPath)) {
      return AssetCheckPayload(AssetStatus.NOT_DOWNLOADED)
    }

    val checkedDate = getCheckedDate(localInstallPath)
    val fileModifiedDate = runCatching {
      Files.getLastModifiedTime(localInstallPath).toInstant()
    }.getOrNull()
    val hasLegacyFalseSuccessTimestamp = checkedDate != null &&
      fileModifiedDate != null &&
      checkedDate.isAfter(fileModifiedDate.plus(LEGACY_FALSE_SUCCESS_TOLERANCE))

    return when {
      hasLegacyFalseSuccessTimestamp -> AssetCheckPayload(
        AssetStatus.STALE,
        metaData = null
      )
      !hasBeenCheckedToday(localInstallPath) -> {
        AssetCheckPayload(
          AssetStatus.STALE,
          checkedDate
        )
      }
      else -> AssetCheckPayload(AssetStatus.CURRENT)
    }
  }
}
