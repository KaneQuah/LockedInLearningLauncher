package com.lockedinlearning.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

enum class IconShape { SYSTEM_DEFAULT, CIRCLE, SQUIRCLE, ROUNDED_SQUARE }

/**
 * Global, app-wide icon-appearance preferences only. Per-item PLACEMENT
 * (dock slots, folders, widget positions, home-screen layout) lives in the Room-backed
 * `favorites` table (see launcher3.model.FavoriteItemEntity/FavoritesDao) — that's spatial
 * data, not a rendering-config toggle, so it doesn't belong in this DataStore.
 */
data class LauncherPreferences(
    val iconShape: IconShape = IconShape.SYSTEM_DEFAULT,
    val iconSizeScale: Float = 1.0f,
    val showIconLabels: Boolean = true
)

@Singleton
class LauncherPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_SIZE_SCALE = floatPreferencesKey("icon_size_scale")
        val SHOW_ICON_LABELS = booleanPreferencesKey("show_icon_labels")
    }

    val prefsFlow: Flow<LauncherPreferences> = context.launcherDataStore.data.map { prefs ->
        LauncherPreferences(
            iconShape = prefs[Keys.ICON_SHAPE]
                ?.let { runCatching { IconShape.valueOf(it) }.getOrNull() }
                ?: IconShape.SYSTEM_DEFAULT,
            iconSizeScale = prefs[Keys.ICON_SIZE_SCALE] ?: 1.0f,
            showIconLabels = prefs[Keys.SHOW_ICON_LABELS] ?: true
        )
    }

    suspend fun setIconShape(shape: IconShape) = context.launcherDataStore.edit {
        it[Keys.ICON_SHAPE] = shape.name
    }

    suspend fun setIconSizeScale(scale: Float) = context.launcherDataStore.edit {
        it[Keys.ICON_SIZE_SCALE] = scale
    }

    suspend fun setShowIconLabels(show: Boolean) = context.launcherDataStore.edit {
        it[Keys.SHOW_ICON_LABELS] = show
    }
}
