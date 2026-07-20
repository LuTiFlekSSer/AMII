package io.unthrottled.amii.assets

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import io.unthrottled.amii.actions.SyncedAssetsListener
import io.unthrottled.amii.assets.LocalVisualContentManager.updateRepresentation
import io.unthrottled.amii.platform.LifeCycleManager
import io.unthrottled.amii.platform.UpdateAssetsListener
import io.unthrottled.amii.services.ExecutionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class VisualEntityRepository : Disposable {
  companion object {
    val instance: VisualEntityRepository
      get() = ApplicationManager.getApplication().getService(VisualEntityRepository::class.java)

    private const val ANIME_SYNC = 1
    private const val CHARACTER_SYNC = 2
    private const val VISUAL_SYNC = 4
    private const val ALL_SYNCED = ANIME_SYNC or CHARACTER_SYNC or VISUAL_SYNC
  }

  private data class RemoteIndices(
    val visualAssets: Map<String, VisualAssetEntity>,
    val anime: Map<String, AnimeEntity>,
    val characters: Map<String, CharacterEntity>
  )

  // Initialize every manager and index before subscribing to their synchronous
  // update events. This avoids re-entering partially initialized Kotlin objects.
  @Volatile
  private var remoteIndices = createRemoteIndices()

  @Volatile
  private var localVisualAssetEntities: MutableMap<String, VisualAssetEntity> = createLocalVisualIndex()

  private val syncLock = Any()
  private var syncedAssets = 0
  private val indexRefreshPending = AtomicBoolean(false)
  private val indexRefreshRunning = AtomicBoolean(false)
  private val messageBusConnection = ApplicationManager.getApplication().messageBus.connect()

  init {
    LifeCycleManager.registerAssetUpdateListener(
      object : UpdateAssetsListener {
        override fun onRequestedUpdate() {
          resetSyncedAssets()
        }

        override fun onRequestedBackgroundUpdate() {
          resetSyncedAssets()
        }
      }
    )

    messageBusConnection.subscribe(
      ContentManagerListener.TOPIC,
      ContentManagerListener { recordSynchronizedAsset(it) }
    )
  }

  private fun updateIndices() {
    remoteIndices = createRemoteIndices()
    refreshLocalAssets()
  }

  fun refreshLocalAssets() {
    localVisualAssetEntities = createLocalVisualIndex()
  }

  val allCharacters: List<CharacterEntity>
    get() = remoteIndices.characters.values.toList()

  fun findById(assetId: String): VisualAssetEntity? {
    return remoteIndices.visualAssets[assetId] ?: localVisualAssetEntities[assetId]
  }

  fun update(visualAssetEntity: VisualAssetEntity) {
    updateRepresentation(
      visualAssetEntity.representation
    )
    localVisualAssetEntities[visualAssetEntity.id] = visualAssetEntity
  }

  private fun createRemoteIndices(): RemoteIndices {
    val anime = AnimeContentManager.supplyAssets()
      .associate { it.id to it.toEntity() }
    val characters = CharacterContentManager.supplyAssets()
      .filter { anime.containsKey(it.animeId) }
      .associate { it.id to it.toEntity(anime.getValue(it.animeId)) }
    val visualAssets = ConcurrentHashMap(
      RemoteVisualContentManager.supplyAllAssetDefinitions()
        .map { representation ->
          representation.toEntity(representation.char.mapNotNull { characters[it] })
        }.associateBy { it.id }
    )
    return RemoteIndices(visualAssets, anime, characters)
  }

  private fun createLocalVisualIndex(): ConcurrentHashMap<String, VisualAssetEntity> {
    return ConcurrentHashMap(
      LocalVisualContentManager.supplyAllUserModifiedVisualRepresentations()
        .map { visualRepresentation ->
          visualRepresentation.fromCustomEntity()
        }.associateBy { it.id }
    )
  }

  private fun resetSyncedAssets() {
    synchronized(syncLock) {
      syncedAssets = 0
    }
  }

  private fun recordSynchronizedAsset(assetCategory: AssetCategory) {
    val allAssetsAreSynchronized = synchronized(syncLock) {
      syncedAssets = syncedAssets or when (assetCategory) {
        AssetCategory.ANIME -> ANIME_SYNC
        AssetCategory.CHARACTERS -> CHARACTER_SYNC
        AssetCategory.VISUALS -> VISUAL_SYNC
        else -> 0
      }
      if (syncedAssets == ALL_SYNCED) {
        syncedAssets = 0
        true
      } else {
        false
      }
    }
    if (allAssetsAreSynchronized) scheduleIndexRefresh()
  }

  private fun scheduleIndexRefresh() {
    indexRefreshPending.set(true)
    if (indexRefreshRunning.compareAndSet(false, true).not()) return

    ExecutionService.executeAsynchronously {
      try {
        while (indexRefreshPending.getAndSet(false)) {
          updateIndices()
          ApplicationManager.getApplication().messageBus.syncPublisher(SyncedAssetsListener.TOPIC)
            .onSynced()
        }
      } finally {
        indexRefreshRunning.set(false)
        if (indexRefreshPending.get()) scheduleIndexRefresh()
      }
    }
  }

  override fun dispose() {
    messageBusConnection.dispose()
  }
}
