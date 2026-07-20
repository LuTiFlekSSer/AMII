package io.unthrottled.amii.listeners

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import io.unthrottled.amii.assets.APIAssetListener
import io.unthrottled.amii.assets.AssetCategory.API
import io.unthrottled.amii.assets.AssetCategory.AUDIBLE
import io.unthrottled.amii.assets.AssetCategory.VISUALS
import io.unthrottled.amii.assets.LocalStorageService
import io.unthrottled.amii.tools.Logging
import io.unthrottled.amii.tools.logger
import io.unthrottled.amii.tools.runSafely
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class OrphanReaper : APIAssetListener, Logging {

  override fun onDownload(apiPath: String) {
    harvestOrphans(apiPath)
  }

  override fun onUpdate(apiPath: String) {
    harvestOrphans(apiPath)
  }

  private fun harvestOrphans(apiPath: String) {
    ApplicationManager.getApplication()
      .executeOnPooledThread {
        val normalizedApiPath = apiPath.substringBefore('?')
        val assetCategory = when (normalizedApiPath.substringAfterLast("/").lowercase()) {
          VISUALS.category -> VISUALS
          AUDIBLE.category -> AUDIBLE
          else -> return@executeOnPooledThread
        }

        val contentDirectory = Paths.get(LocalStorageService.getContentDirectory())
        val activePaths = readActivePathsFromCache(contentDirectory, normalizedApiPath)
        if (activePaths == null) {
          // Reaping is destructive. If the freshly committed definition file
          // cannot be read, retaining stale binaries is safer than deleting
          // files that may still be active.
          logger().warn("Skipping orphan harvest because $normalizedApiPath could not be read")
          return@executeOnPooledThread
        }

        val assetDirectory = contentDirectory.resolve(assetCategory.category)
        findOrphanedBinaries(assetDirectory, activePaths)
          .forEach { orphanedBinary ->
            logger().warn("Harvesting orphaned binary $orphanedBinary")
            runSafely({
              Files.delete(orphanedBinary)
            }) {
              logger().warn("Harvesting orphaned binary $it failed for reasons", it)
            }
          }
      }
  }
}

/**
 * Reads the definition file which APIAssetManager atomically commits before it
 * publishes APIAssetListener events. This deliberately avoids the content
 * manager's asynchronously refreshed in-memory snapshot.
 */
internal fun readActivePathsFromCache(
  contentDirectory: Path,
  apiPath: String
): Set<String>? {
  val apiDirectory = contentDirectory.resolve(API.category).normalize().toAbsolutePath()
  val relativeApiPath = apiPath
    .substringBefore('?')
    .trimStart('/', '\\')
    .replace('\\', '/')
  val definitionPath = apiDirectory.resolve(relativeApiPath).normalize().toAbsolutePath()
  if (!definitionPath.startsWith(apiDirectory) || !Files.isRegularFile(definitionPath)) return null

  return runCatching {
    Files.newBufferedReader(definitionPath).use { reader ->
      val definitions = JsonParser.parseReader(reader)
      if (!definitions.isJsonArray) return null

      definitions.asJsonArray
        .asSequence()
        .mapNotNull { it.takeIf { definition -> definition.isJsonObject }?.asJsonObject }
        .filter { definition ->
          definition.get("del")?.let { deleted ->
            deleted.isJsonPrimitive && deleted.asJsonPrimitive.isBoolean && deleted.asBoolean
          } != true
        }
        .mapNotNull { definition ->
          definition.get("path")
            ?.takeIf { path -> path.isJsonPrimitive && path.asJsonPrimitive.isString }
            ?.asString
        }
        .map(::normalizeAssetPath)
        .filter(String::isNotEmpty)
        .toSet()
    }
  }.getOrNull()
}

internal fun findOrphanedBinaries(
  assetDirectory: Path,
  activePaths: Set<String>
): List<Path> {
  if (!Files.isDirectory(assetDirectory)) return emptyList()

  return Files.walk(assetDirectory).use { paths ->
    paths
      .filter { Files.isRegularFile(it) }
      .filter { isAtomicWriteTemporaryFile(it).not() }
      .filter { binary ->
        normalizeAssetPath(assetDirectory.relativize(binary).toString()) !in activePaths
      }
      .collect(Collectors.toList())
  }
}

private fun normalizeAssetPath(path: String): String =
  path.replace('\\', '/').trimStart('/')

private fun isAtomicWriteTemporaryFile(path: Path): Boolean {
  val fileName = path.fileName.toString()
  return fileName.startsWith('.') && fileName.endsWith(".part")
}
