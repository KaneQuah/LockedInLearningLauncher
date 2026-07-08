package com.lockedinlearning.launcher3.model

/**
 * In-memory placed-item model, joining a [FavoriteItemEntity] row with its live
 * PackageManager label (apps don't store their own label — it can change/localize).
 * Mirrors AOSP's ItemInfo/AppInfo/FolderInfo/LauncherAppWidgetInfo split, collapsed
 * into one sealed class since this launcher doesn't need Launcher3's full class hierarchy.
 */
sealed class ItemInfo {
    abstract val entity: FavoriteItemEntity

    data class AppItem(
        override val entity: FavoriteItemEntity,
        val label: String
    ) : ItemInfo()

    data class FolderItem(
        override val entity: FavoriteItemEntity,
        val label: String,
        val contents: List<AppItem>
    ) : ItemInfo()

    data class WidgetItem(
        override val entity: FavoriteItemEntity
    ) : ItemInfo()
}

/** A plain installed-app record (drawer list, folder contents, dock) — not yet placed anywhere. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String = ""
)
