package io.unthrottled.amii.memes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import io.unthrottled.amii.assets.MemeAsset
import io.unthrottled.amii.assets.MemeAssetCategory
import io.unthrottled.amii.assets.MemeAssetService
import io.unthrottled.amii.events.UserEvent
import io.unthrottled.amii.onboarding.UpdateNotification
import io.unthrottled.amii.services.ExecutionService
import io.unthrottled.amii.tools.AssetTools
import io.unthrottled.amii.tools.PluginMessageBundle
import io.unthrottled.amii.tools.doOrElse
import java.util.Optional

fun Project.memeEventService(): MemeEventService = this.getService(MemeEventService::class.java)

class MemeEventService(private val project: Project) {

  fun displayLastMemeEvent() {
    runOnUiThread {
      val memeToShow = previousMemeEvent ?: return@runOnUiThread
      displayMemeEvent(memeToShow.clone())
    }
  }

  fun createAndDisplayMemeEventFromCategory(
    userEvent: UserEvent,
    memeAssetCategory: MemeAssetCategory,
    memeDecorator: (Meme.Builder) -> MemeEvent
  ) {
    buildMemeEvent(memeDecorator, userEvent) { MemeAssetService.getFromCategory(memeAssetCategory) }
  }

  fun createAndDisplayMemeEventFromCategories(
    userEvent: UserEvent,
    vararg memeAssetCategories: MemeAssetCategory,
    memeDecorator: (Meme.Builder) -> MemeEvent = { MemeEvent(it.build(), userEvent) }
  ) {
    buildMemeEvent(memeDecorator, userEvent) { AssetTools.resolveAssetFromCategories(*memeAssetCategories) }
  }

  private fun buildMemeEvent(
    memeDecorator: (Meme.Builder) -> MemeEvent,
    userEvent: UserEvent,
    memeSupplier: () -> Optional<MemeAsset>
  ) {
    ExecutionService.executeAsynchronously {
      val suppliedMeme = memeSupplier().map { memeAsset ->
        memeAsset to MemePanel.calculateCappedDimensions(memeAsset.visualMemeContent)
      }
      runOnUiThread {
        suppliedMeme.flatMap { (memeAsset, cappedDimensions) ->
          project.memeFactory()
            .getMemeBuilderForAsset(
              memeAsset,
              cappedDimensions
            )
        }.map { memeBuilder ->
          memeDecorator(memeBuilder)
        }.doOrElse({
          attemptToDisplayMeme(it)
        }) {
          UpdateNotification.sendMessage(
            PluginMessageBundle.message("notification.no-memes.title", userEvent.eventName),
            PluginMessageBundle.message("notification.no-memes.body"),
            project
          )
        }
      }
    }
  }

  private fun runOnUiThread(action: () -> Unit) {
    val application = ApplicationManager.getApplication()
    val guardedAction = {
      if (project.isDisposed.not()) action()
    }
    if (application.isDispatchThread) {
      guardedAction()
    } else {
      application.invokeLater({ guardedAction() }, ModalityState.any())
    }
  }

  private var displayedMemeEvent: MemeEvent? = null
  private var previousMemeEvent: MemeEvent? = null
  private fun attemptToDisplayMeme(memeEvent: MemeEvent) {
    val currentlyDisplayedMeme = displayedMemeEvent
    val comparison = currentlyDisplayedMeme?.compareTo(memeEvent) ?: Comparison.UNKNOWN
    if (comparison == Comparison.GREATER || comparison == Comparison.UNKNOWN) {
      currentlyDisplayedMeme?.meme?.dismiss()
      previousMemeEvent = currentlyDisplayedMeme ?: previousMemeEvent
      displayMemeEvent(memeEvent)
    } else {
      memeEvent.dispose()
    }
  }

  private fun displayMemeEvent(memeEvent: MemeEvent) {
    displayedMemeEvent = memeEvent
    val meme = memeEvent.meme
    meme.addListener(
      object : MemeLifecycleListener {
        override fun onRemoval() {
          displayedMemeEvent = null
          previousMemeEvent = memeEvent
        }
      }
    )
    project.memeService().displayMeme(meme)
  }
}
