package com.lockedinlearning.launcher3

/**
 * Constants mirroring AOSP Launcher3's `com.android.launcher3.model.LauncherSettings.Favorites`,
 * simplified for a single-screen setup (no multi-page Workspace, no cross-process ContentProvider —
 * a single Room table is enough for one app's own launcher core).
 */
object LauncherSettings {
    const val CONTAINER_DESKTOP = -100L
    const val CONTAINER_HOTSEAT = -101L

    const val ITEM_TYPE_APPLICATION = 0
    const val ITEM_TYPE_FOLDER = 2
    const val ITEM_TYPE_APPWIDGET = 4

    /** Fixed single Workspace screen — no paging. */
    const val SCREEN_ID_DESKTOP = 0

    /** Workspace is a fixed GRID_SIZE x GRID_SIZE grid. */
    const val GRID_SIZE = 5

    /** Hotseat is a fixed single row of HOTSEAT_SIZE slots. */
    const val HOTSEAT_SIZE = 5
}
