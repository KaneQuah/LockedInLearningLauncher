package com.lockedinlearning.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.data.datastore.GatePreferencesDataStore
import com.lockedinlearning.data.datastore.LauncherPreferencesDataStore
import com.lockedinlearning.data.repository.ProgressRepository
import com.lockedinlearning.launcher3.LauncherSettings
import com.lockedinlearning.launcher3.model.AppInfo
import com.lockedinlearning.launcher3.model.FavoriteItemEntity
import com.lockedinlearning.launcher3.model.FavoritesDao
import com.lockedinlearning.launcher3.model.GridOccupancy
import com.lockedinlearning.launcher3.model.ItemInfo
import com.lockedinlearning.notifications.NotificationBadgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val streak: Int = 0,
    val todayCorrect: Int = 0,
    val gateDisabledUntil: Long = 0L,
    val installedApps: List<AppInfo> = emptyList(),
    /** Top-level Workspace items (container = CONTAINER_DESKTOP). Folder contents are nested inside FolderItem. */
    val desktopItems: List<ItemInfo> = emptyList(),
    /** Hotseat items, ordered by slot (cellX 0-4). */
    val hotseatItems: List<ItemInfo> = emptyList(),
    val iconAppearance: IconAppearance = IconAppearance(),
    val notificationBadges: Map<String, Int> = emptyMap(),
    val showBadgeCount: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progressRepository: ProgressRepository,
    private val prefsStore: GatePreferencesDataStore,
    private val launcherPrefs: LauncherPreferencesDataStore,
    private val favoritesDao: FavoritesDao,
    private val badgeRepository: NotificationBadgeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Fallback dock choices used only to seed the Hotseat the very first time it's empty. */
    private val defaultDockPackages = listOf(
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.chrome", "com.samsung.android.app.sbrowser",
        "com.google.android.camera", "com.samsung.android.camera", "android.camera"
    )

    private data class Snapshot(
        val todayCorrect: Int,
        val dailyGoal: Int,
        val gateDisabledUntil: Long,
        val iconAppearance: IconAppearance,
        val notificationBadgesEnabled: Boolean,
        val showBadgeCount: Boolean,
        val apps: List<AppInfo>,
        val badges: Map<String, Int>
    )

    init {
        loadApps()
        viewModelScope.launch {
            val base = combine(
                progressRepository.observeTodayCorrect(),
                prefsStore.prefsFlow,
                launcherPrefs.prefsFlow,
                installedApps,
                badgeRepository.badges
            ) { todayCorrect, gatePrefs, launcher, apps, badges ->
                Snapshot(
                    todayCorrect = todayCorrect,
                    dailyGoal = gatePrefs.dailyGoal,
                    gateDisabledUntil = gatePrefs.disableUntilEpoch,
                    iconAppearance = IconAppearance(launcher.iconShape, launcher.iconSizeScale, launcher.showIconLabels),
                    notificationBadgesEnabled = launcher.notificationBadgesEnabled,
                    showBadgeCount = launcher.notificationBadgeShowCount,
                    apps = apps,
                    badges = badges
                )
            }
            combine(base, favoritesDao.observeAll()) { snap, allRows -> snap to allRows }
                .collect { (snap, allRows) ->
                    seedHotseatIfEmpty(allRows, snap.apps)

                    val streak = progressRepository.computeStreak(goal = snap.dailyGoal)
                    val byPackage = snap.apps.associateBy { it.packageName }

                    val byContainer = allRows.groupBy { it.container }
                    val desktopRows = byContainer[LauncherSettings.CONTAINER_DESKTOP].orEmpty()
                    val hotseatRows = byContainer[LauncherSettings.CONTAINER_HOTSEAT].orEmpty()
                        .sortedBy { it.cellX }

                    fun toItemInfo(row: FavoriteItemEntity): ItemInfo? = when (row.itemType) {
                        LauncherSettings.ITEM_TYPE_APPLICATION -> {
                            val app = byPackage[row.packageName] ?: return null
                            ItemInfo.AppItem(row, app.label)
                        }
                        LauncherSettings.ITEM_TYPE_FOLDER -> {
                            val contents = byContainer[row.id].orEmpty()
                                .sortedBy { it.cellX }
                                .mapNotNull { child -> byPackage[child.packageName]?.let { ItemInfo.AppItem(child, it.label) } }
                            ItemInfo.FolderItem(row, row.title ?: "Folder", contents)
                        }
                        LauncherSettings.ITEM_TYPE_APPWIDGET -> ItemInfo.WidgetItem(row)
                        else -> null
                    }

                    _state.update {
                        it.copy(
                            streak = streak,
                            todayCorrect = snap.todayCorrect,
                            gateDisabledUntil = snap.gateDisabledUntil,
                            installedApps = snap.apps,
                            desktopItems = desktopRows.mapNotNull(::toItemInfo),
                            hotseatItems = hotseatRows.mapNotNull(::toItemInfo),
                            iconAppearance = snap.iconAppearance,
                            notificationBadges = if (snap.notificationBadgesEnabled) snap.badges else emptyMap(),
                            showBadgeCount = snap.showBadgeCount
                        )
                    }
                }
        }
    }

    /** One-time seed: if the Hotseat has never been populated, fill it from the heuristic default list. */
    private suspend fun seedHotseatIfEmpty(allRows: List<FavoriteItemEntity>, apps: List<AppInfo>) {
        val hasHotseat = allRows.any { it.container == LauncherSettings.CONTAINER_HOTSEAT }
        if (hasHotseat || apps.isEmpty()) return
        val byPackage = apps.associateBy { it.packageName }
        val seeded = defaultDockPackages.mapNotNull { byPackage[it] }
            .ifEmpty { apps.take(LauncherSettings.HOTSEAT_SIZE) }
            .take(LauncherSettings.HOTSEAT_SIZE)
        favoritesDao.insertAll(
            seeded.mapIndexed { index, app ->
                FavoriteItemEntity(
                    itemType = LauncherSettings.ITEM_TYPE_APPLICATION,
                    container = LauncherSettings.CONTAINER_HOTSEAT,
                    cellX = index,
                    cellY = 0,
                    packageName = app.packageName
                )
            }
        )
    }

    fun refreshApps() = loadApps()

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("DEPRECATION")
                val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    )
                } else {
                    pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                }
                resolveInfoList
                    .map { ri ->
                        AppInfo(
                            label = ri.loadLabel(pm).toString(),
                            packageName = ri.activityInfo.packageName,
                            activityName = ri.activityInfo.name
                        )
                    }
                    .filter { it.packageName != context.packageName }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            installedApps.value = apps
            favoritesDao.pruneUninstalled(apps.map { it.packageName })
        }
    }

    // ── Hotseat ─────────────────────────────────────────────────────────────

    /** Replace (or place) the app at Hotseat slot [slot]. */
    fun setDockApp(slot: Int, packageName: String) {
        viewModelScope.launch {
            val hotseat = favoritesDao.getHotseatOnce()
            val existing = hotseat.find { it.cellX == slot }
            if (existing != null) {
                favoritesDao.update(existing.copy(packageName = packageName))
            } else {
                favoritesDao.insert(
                    FavoriteItemEntity(
                        itemType = LauncherSettings.ITEM_TYPE_APPLICATION,
                        container = LauncherSettings.CONTAINER_HOTSEAT,
                        cellX = slot,
                        cellY = 0,
                        packageName = packageName
                    )
                )
            }
        }
    }

    // ── Workspace (desktop) ──────────────────────────────────────────────────

    /** Places an app on the first free desktop cell. No-op if already placed or the grid is full. */
    fun addAppToHome(packageName: String) {
        viewModelScope.launch {
            val desktop = favoritesDao.getDesktopOnce()
            if (desktop.any { it.itemType == LauncherSettings.ITEM_TYPE_APPLICATION && it.packageName == packageName }) return@launch
            val cell = GridOccupancy.findEmptyCell(desktop) ?: return@launch
            favoritesDao.insert(
                FavoriteItemEntity(
                    itemType = LauncherSettings.ITEM_TYPE_APPLICATION,
                    container = LauncherSettings.CONTAINER_DESKTOP,
                    cellX = cell.first,
                    cellY = cell.second,
                    packageName = packageName
                )
            )
        }
    }

    fun removeFromHome(itemId: Long) {
        viewModelScope.launch { favoritesDao.deleteById(itemId) }
    }

    /** Moves an item (from anywhere — desktop, hotseat, or a folder) onto the desktop at a cell, via DragController. */
    fun moveDesktopItem(itemId: Long, cellX: Int, cellY: Int) {
        viewModelScope.launch {
            val all = favoritesDao.getAll()
            val item = all.find { it.id == itemId } ?: return@launch
            val desktopOthers = all.filter { it.container == LauncherSettings.CONTAINER_DESKTOP && it.id != itemId }
            if (GridOccupancy.collides(cellX, cellY, item.spanX, item.spanY, desktopOthers)) return@launch
            favoritesDao.update(item.copy(container = LauncherSettings.CONTAINER_DESKTOP, cellX = cellX, cellY = cellY))
            deleteFolderIfNowEmpty(previousContainer = item.container, all = all, movedItemId = itemId)
        }
    }

    /** Moves an item (from anywhere) onto a Hotseat slot, via DragController. No-op if the slot is already taken. */
    fun moveHotseatItem(itemId: Long, slot: Int) {
        viewModelScope.launch {
            val all = favoritesDao.getAll()
            val item = all.find { it.id == itemId } ?: return@launch
            val occupant = all.find {
                it.container == LauncherSettings.CONTAINER_HOTSEAT && it.cellX == slot && it.id != itemId
            }
            if (occupant != null) return@launch
            favoritesDao.update(item.copy(container = LauncherSettings.CONTAINER_HOTSEAT, cellX = slot, cellY = 0))
            deleteFolderIfNowEmpty(previousContainer = item.container, all = all, movedItemId = itemId)
        }
    }

    /** Dragging an item out of a folder (onto the desktop or hotseat) uses the same generic
     *  move* functions as any other drag — mirror removeFromFolder's "dissolve when empty"
     *  behavior here too, so drag-out and the explicit remove path stay consistent. */
    private suspend fun deleteFolderIfNowEmpty(previousContainer: Long, all: List<FavoriteItemEntity>, movedItemId: Long) {
        val wasInFolder = all.any { it.id == previousContainer && it.itemType == LauncherSettings.ITEM_TYPE_FOLDER }
        if (!wasInFolder) return
        val remaining = all.count { it.container == previousContainer && it.id != movedItemId }
        if (remaining == 0) favoritesDao.deleteById(previousContainer)
    }

    // ── Folders ─────────────────────────────────────────────────────────────

    /** Fuses two desktop/hotseat apps into a new folder placed where [anchorItemId] currently sits. */
    fun createFolder(name: String, anchorItemId: Long, otherItemId: Long) {
        viewModelScope.launch {
            val all = favoritesDao.getAll()
            val anchor = all.find { it.id == anchorItemId } ?: return@launch
            val other = all.find { it.id == otherItemId } ?: return@launch
            val folderId = favoritesDao.insert(
                FavoriteItemEntity(
                    itemType = LauncherSettings.ITEM_TYPE_FOLDER,
                    container = anchor.container,
                    cellX = anchor.cellX,
                    cellY = anchor.cellY,
                    title = name.ifBlank { "Folder" }
                )
            )
            favoritesDao.update(anchor.copy(container = folderId, cellX = 0, cellY = 0))
            favoritesDao.update(other.copy(container = folderId, cellX = 1, cellY = 0))
        }
    }

    fun addToFolder(folderId: Long, itemId: Long) {
        viewModelScope.launch {
            val contents = favoritesDao.getAll().filter { it.container == folderId }
            val item = favoritesDao.getAll().find { it.id == itemId } ?: return@launch
            favoritesDao.update(item.copy(container = folderId, cellX = contents.size, cellY = 0))
        }
    }

    /** Persists a drag-reordered folder contents list — [orderedItemIds] is the new front-to-back
     *  order, encoded the same way folder membership already is (container = folderId, cellX =
     *  rank within the folder), so no schema/read-side change is needed beyond sorting by cellX. */
    fun reorderFolder(folderId: Long, orderedItemIds: List<Long>) {
        viewModelScope.launch {
            val all = favoritesDao.getAll()
            val byId = all.associateBy { it.id }
            orderedItemIds.forEachIndexed { index, itemId ->
                val item = byId[itemId] ?: return@forEachIndexed
                if (item.container == folderId && item.cellX == index) return@forEachIndexed
                favoritesDao.update(item.copy(container = folderId, cellX = index, cellY = 0))
            }
        }
    }

    /** Removes an app from a folder back onto the desktop; deletes the folder if it's left empty. */
    fun removeFromFolder(folderId: Long, itemId: Long) {
        viewModelScope.launch {
            val all = favoritesDao.getAll()
            val folder = all.find { it.id == folderId } ?: return@launch
            val item = all.find { it.id == itemId } ?: return@launch
            val desktop = all.filter { it.container == LauncherSettings.CONTAINER_DESKTOP }
            val cell = GridOccupancy.findEmptyCell(desktop) ?: (folder.cellX to folder.cellY)
            favoritesDao.update(item.copy(container = LauncherSettings.CONTAINER_DESKTOP, cellX = cell.first, cellY = cell.second))
            val remaining = all.count { it.container == folderId && it.id != itemId }
            if (remaining == 0) favoritesDao.delete(folder)
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            val folder = favoritesDao.getAll().find { it.id == folderId } ?: return@launch
            favoritesDao.update(folder.copy(title = newName.ifBlank { folder.title }))
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            favoritesDao.deleteFolderContents(folderId)
            favoritesDao.deleteById(folderId)
        }
    }

    /** Dissolves a folder, unlike [deleteFolder] — its contents move back onto the desktop as
     *  individual icons instead of being dropped from Home entirely. */
    fun ungroupFolder(folderId: Long) {
        viewModelScope.launch {
            val all = favoritesDao.getAll()
            val folder = all.find { it.id == folderId } ?: return@launch
            val children = all.filter { it.container == folderId }
            var desktopSnapshot = all.filter { it.container == LauncherSettings.CONTAINER_DESKTOP }
            children.forEach { child ->
                val cell = GridOccupancy.findEmptyCell(desktopSnapshot) ?: (folder.cellX to folder.cellY)
                val updated = child.copy(container = LauncherSettings.CONTAINER_DESKTOP, cellX = cell.first, cellY = cell.second)
                favoritesDao.update(updated)
                desktopSnapshot = desktopSnapshot + updated
            }
            favoritesDao.deleteById(folderId)
        }
    }

    // ── Widgets ─────────────────────────────────────────────────────────────

    /** [targetCell] lets a caller place at a user-chosen cell (e.g. after a preview-then-drag
     *  placement); omitted, it falls back to the first free cell like before. */
    fun addWidget(widgetId: Int, spanX: Int = 2, spanY: Int = 2, targetCell: Pair<Int, Int>? = null) {
        viewModelScope.launch {
            val desktop = favoritesDao.getDesktopOnce()
            val cell = targetCell ?: GridOccupancy.findEmptyCell(desktop, spanX, spanY) ?: return@launch
            favoritesDao.insert(
                FavoriteItemEntity(
                    itemType = LauncherSettings.ITEM_TYPE_APPWIDGET,
                    container = LauncherSettings.CONTAINER_DESKTOP,
                    cellX = cell.first,
                    cellY = cell.second,
                    spanX = spanX,
                    spanY = spanY,
                    appWidgetId = widgetId
                )
            )
        }
    }

    fun removeWidget(itemId: Long, widgetId: Int, host: WidgetHostManager) {
        viewModelScope.launch {
            favoritesDao.deleteById(itemId)
            host.deleteWidgetId(widgetId)
        }
    }
}
