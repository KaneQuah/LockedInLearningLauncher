package com.lockedinlearning.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory package-name → active-notification-count map, fed live by
 * [LauncherNotificationListenerService]. Never persisted — badge state is
 * only ever "what's currently posted," sourced fresh each time the listener
 * connects.
 */
@Singleton
class NotificationBadgeRepository @Inject constructor() {

    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())
    val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()

    fun onNotificationPosted(packageName: String) {
        _badges.update { it + (packageName to (it[packageName] ?: 0) + 1) }
    }

    fun onNotificationRemoved(packageName: String) {
        _badges.update { current ->
            val count = (current[packageName] ?: 0) - 1
            if (count > 0) current + (packageName to count) else current - packageName
        }
    }

    /** Rebuild the whole map from the listener's currently-active notifications (on (re)connect). */
    fun replaceAll(counts: Map<String, Int>) {
        _badges.value = counts
    }

    fun clear() {
        _badges.value = emptyMap()
    }
}
