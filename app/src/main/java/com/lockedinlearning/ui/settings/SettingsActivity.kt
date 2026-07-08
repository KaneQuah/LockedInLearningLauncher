package com.lockedinlearning.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lockedinlearning.ui.theme.LockedInLearningTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // There's no grant broadcast for notification-listener access, so re-check on
        // every resume (covers the user returning from the system Settings screen).
        viewModel.refreshNotificationAccessState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_START_SCREEN) == "progress") {
            viewModel.navigateTo(SettingsScreen.PROGRESS)
        }
        setContent {
            LockedInLearningTheme {
                val state = viewModel.state.collectAsStateWithLifecycle()
                SettingsNavHost(
                    state = state.value,
                    onGateToggle = viewModel::setGateEnabled,
                    onCooldownChange = viewModel::setCooldown,
                    onPinSet = viewModel::setPin,
                    onPinVerify = viewModel::verifyPin,
                    onPauseGate = viewModel::pauseGate,
                    onActiveDeckChange = viewModel::setActiveDeck,
                    onEditDeck = viewModel::editDeck,
                    onIconShapeChange = viewModel::setIconShape,
                    onIconSizeChange = viewModel::setIconSizeScale,
                    onShowLabelsToggle = viewModel::setShowIconLabels,
                    onNotificationBadgesToggle = viewModel::setNotificationBadgesEnabled,
                    onNotificationBadgeShowCountToggle = viewModel::setNotificationBadgeShowCount,
                    onRequestNotificationAccess = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    onNavigate = viewModel::navigateTo,
                    onNavigateBack = viewModel::navigateBack,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_START_SCREEN = "start_screen"
    }
}
