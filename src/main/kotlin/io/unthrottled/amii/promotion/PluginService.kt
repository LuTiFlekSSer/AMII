package io.unthrottled.amii.promotion

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import io.unthrottled.amii.config.Constants
import io.unthrottled.amii.integrations.NetworkTimeouts
import io.unthrottled.amii.tools.Logging
import io.unthrottled.amii.tools.logger
import io.unthrottled.amii.tools.runSafelyWithResult
import io.unthrottled.amii.tools.toOptional
import java.util.Collections

object PluginService : Logging {

  private val gson by lazy { Gson() }

  private val PLUGIN_MANAGER_URL by lazy {
    ApplicationInfo.getInstance()
      .toOptional()
      .filter { it is ApplicationInfoEx }
      .map { it as ApplicationInfoEx }
      .map { it.pluginManagerUrl }
      .orElseGet { "https://plugins.jetbrains.com" }
      .trimEnd('/')
  }
  private val COMPATIBLE_UPDATE_URL by lazy { "$PLUGIN_MANAGER_URL/api/search/compatibleUpdates" }

  fun isRiderExtensionInstalled(): Boolean = PluginManagerCore.isPluginInstalled(
    PluginId.getId(Constants.RIDER_EXTENSION_ID)
  )

  fun isAndroidExtensionInstalled(): Boolean = PluginManagerCore.isPluginInstalled(
    PluginId.getId(Constants.ANDROID_EXTENSION_ID)
  )

  fun canRiderExtensionBeInstalled(): Boolean =
    canExtensionBeInstalled(Constants.RIDER_EXTENSION_ID)

  fun canAndroidExtensionBeInstalled(): Boolean =
    canExtensionBeInstalled(Constants.ANDROID_EXTENSION_ID)

  private fun canExtensionBeInstalled(pluginIdString: String) =
    runSafelyWithResult({
      val pluginId = PluginId.getId(pluginIdString)
      getLastCompatiblePluginUpdate(
        Collections.singleton(pluginId)
      ).firstOrNull { pluginNode ->
        pluginNode.pluginId == pluginIdString
      } != null
    }) {
      false
    }

  private data class CompatibleUpdateRequest(
    val build: String,
    val pluginXMLIds: List<String>
  )

  /**
   * Object from Search Service for getting compatible updates for IDE.
   * [externalUpdateId] update ID from Plugin Repository database.
   * [externalPluginId] plugin ID from Plugin Repository database.
   */
  data class IdeCompatibleUpdate(
    @SerializedName("id")
    val externalUpdateId: String = "",
    @SerializedName("pluginId")
    val externalPluginId: String = "",
    @SerializedName("pluginXmlId")
    val pluginId: String = "",
    val version: String = ""
  )

  private fun getLastCompatiblePluginUpdate(
    ids: Set<PluginId>
  ): List<IdeCompatibleUpdate> {
    return try {
      if (ids.isEmpty()) {
        return emptyList()
      }

      val data = gson.toJson(
        CompatibleUpdateRequest(
          ApplicationInfo.getInstance()
            .build.asString(),
          ids.map { it.idString }
        )
      )
      HttpRequests
        .post(Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm(), HttpRequests.JSON_CONTENT_TYPE)
        .connectTimeout(NetworkTimeouts.CONNECT_MS)
        .readTimeout(NetworkTimeouts.READ_MS)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          it.inputStream.bufferedReader().use { reader ->
            gson.fromJson(reader, object : TypeToken<List<IdeCompatibleUpdate>>() {}) ?: emptyList()
          }
        }
    } catch (e: Exception) {
      logger().warn("Unable to check to see if plugin $ids is compatible for reasons", e)
      emptyList()
    }
  }
}
