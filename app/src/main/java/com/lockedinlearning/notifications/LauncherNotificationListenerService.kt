package com.lockedinlearning.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Powers the optional "notification badges" home-screen feature. Requires the
 * user to grant notification-listener access manually via system Settings
 * (see SettingsScreen's "Notification badges" toggle) — there is no in-app
 * runtime-permission dialog for this permission class. Only existence/count
 * per package is read; notification content/text is never inspected or stored.
 */
@AndroidEntryPoint
class LauncherNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var badgeRepository: NotificationBadgeRepository

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshAll()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        badgeRepository.onNotificationPosted(sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        badgeRepository.onNotificationRemoved(sbn.packageName)
    }

    private fun refreshAll() {
        val counts = runCatching { activeNotifications }
            .getOrNull()
            ?.groupingBy { it.packageName }
            ?.eachCount()
            ?: emptyMap()
        badgeRepository.replaceAll(counts)
    }
}
