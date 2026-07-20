package io.unthrottled.amii.platform

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import io.unthrottled.amii.assets.APIAssetListener

object LifeCycleManager : Disposable {

  private val connection = ApplicationManager.getApplication().messageBus.connect()

  fun registerAssetUpdateListener(updateAssetsListener: UpdateAssetsListener) {
    connection.subscribe(UpdateAssetsListener.TOPIC, updateAssetsListener)
  }

  fun registerAPIAssetUpdateListener(apiAssetListener: APIAssetListener) {
    connection.subscribe(APIAssetListener.TOPIC, apiAssetListener)
  }

  override fun dispose() {
    connection.dispose()
  }
}
