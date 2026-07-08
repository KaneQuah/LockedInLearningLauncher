package com.lockedinlearning.launcher3.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockedinlearning.launcher3.LauncherSettings

/**
 * Mirrors AOSP Launcher3's `LauncherSettings.Favorites` row shape, simplified for a
 * single-screen setup. One row = one placed item (app / folder / widget) on either the
 * Workspace (`container = CONTAINER_DESKTOP`) or the Hotseat (`container = CONTAINER_HOTSEAT`).
 *
 * Folder contents nest exactly like real AOSP: a folder is itself one row here
 * (`itemType = ITEM_TYPE_FOLDER`), and its member apps are separate rows whose
 * `container` is that folder row's own [id] — no separate join table needed.
 *
 * For Hotseat rows, [cellX] IS the 0-4 slot index and [cellY] is always 0 (this is how
 * real AOSP encodes hotseat position too — there is no separate "slotIndex" column).
 */
@Entity(tableName = "favorites", indices = [Index("container"), Index("packageName")])
data class FavoriteItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: Int,
    val container: Long,
    val screen: Int = LauncherSettings.SCREEN_ID_DESKTOP,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val packageName: String? = null,
    val className: String? = null,
    val title: String? = null,
    val appWidgetId: Int? = null,
    val appWidgetProvider: String? = null
)
