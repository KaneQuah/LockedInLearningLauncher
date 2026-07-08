package com.lockedinlearning.ui.home

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the [AppWidgetHost] for the launcher.
 *
 * Lifecycle: HomeActivity calls [startListening]/[stopListening] in onStart/onStop.
 * Host views are cached per widget id so scrolling the home grid doesn't
 * recreate them (which resets widget state and flickers).
 */
@Singleton
class WidgetHostManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val HOST_ID = 0x4C494C // "LIL"
    }

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)
    private val viewCache = mutableMapOf<Int, AppWidgetHostView>()

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun allocateWidgetId(): Int = host.allocateAppWidgetId()

    fun deleteWidgetId(id: Int) {
        viewCache.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun getWidgetInfo(id: Int): AppWidgetProviderInfo? =
        runCatching { appWidgetManager.getAppWidgetInfo(id) }.getOrNull()

    fun bindWidgetIdIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        }.getOrDefault(false)

    /** All widget providers installed on the device, sorted by label. */
    fun installedProviders(): List<AppWidgetProviderInfo> =
        runCatching { appWidgetManager.installedProviders }
            .getOrDefault(emptyList())
            .sortedBy { it.loadLabel(context.packageManager).lowercase() }

    fun providerLabel(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(context.packageManager) }.getOrNull() ?: info.provider.shortClassName

    private val previewCache = mutableMapOf<ComponentName, Drawable?>()

    /** The picker shows this per provider so users see what they're adding before committing —
     *  falls back to the provider's icon since a widget can legally omit a preview image. */
    fun previewFor(info: AppWidgetProviderInfo): Drawable? = previewCache.getOrPut(info.provider) {
        runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
            ?: runCatching { info.loadIcon(context, 0) }.getOrNull()
    }

    /**
     * Create (or reuse) the host view for [id]. Detaches from any previous parent so it can be
     * re-attached by the caller's own layout.
     *
     * [widthDp]/[heightDp], when known, are pushed to the widget via [AppWidgetHostView
     * .updateAppWidgetSize] — without this, many RemoteViews providers (clocks, search bars,
     * calendars) never learn how much space they actually have and fall back to rendering for
     * their default/maximum size, which is what makes a widget look oversized or visually broken
     * even though CellLayout is already constraining its measured bounds correctly.
     */
    fun createView(activityContext: Context, id: Int, widthDp: Int? = null, heightDp: Int? = null): AppWidgetHostView? {
        val info = getWidgetInfo(id) ?: return null
        val view = viewCache.getOrPut(id) {
            host.createView(activityContext, id, info)
        }
        (view.parent as? ViewGroup)?.removeView(view)
        if (widthDp != null && heightDp != null && widthDp > 0 && heightDp > 0) {
            runCatching { view.updateAppWidgetSize(android.os.Bundle(), widthDp, heightDp, widthDp, heightDp) }
        }
        return view
    }

    fun startConfigure(activity: android.app.Activity, id: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
    }
}
