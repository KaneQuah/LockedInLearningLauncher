package com.lockedinlearning.ui.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.data.datastore.GatePreferencesDataStore
import com.lockedinlearning.data.datastore.IconShape
import com.lockedinlearning.data.datastore.LauncherPreferencesDataStore
import com.lockedinlearning.data.repository.DeckRepository
import com.lockedinlearning.domain.model.Deck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt
import javax.inject.Inject

data class SettingsUiState(
    val gateEnabled: Boolean = true,
    val cooldownMinutes: Int = 3,
    val hasPinSet: Boolean = false,
    val disableUntilEpoch: Long = 0L,
    val activeDeckId: String = "",
    val editingDeckId: String? = null,
    val decks: List<Deck> = emptyList(),
    val pinVerified: Boolean = false,
    val pinError: Boolean = false,
    val screen: SettingsScreen = SettingsScreen.PIN_GATE,
    val iconShape: IconShape = IconShape.SYSTEM_DEFAULT,
    val iconSizeScale: Float = 1.0f,
    val showIconLabels: Boolean = true,
    val notificationBadgesEnabled: Boolean = false,
    val notificationBadgeShowCount: Boolean = true,
    /** Whether the OS has actually granted notification-listener access — separate from the app-level toggle above. */
    val notificationAccessGranted: Boolean = false
)

enum class SettingsScreen { PIN_GATE, MAIN, DISABLE_PICKER, DECK_MANAGER, QUESTION_EDITOR, PROGRESS }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: GatePreferencesDataStore,
    private val deckRepository: DeckRepository,
    private val launcherPrefs: LauncherPreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefsStore.prefsFlow,
                deckRepository.observeDecks(),
                launcherPrefs.prefsFlow
            ) { prefs, decks, launcher -> Triple(prefs, decks, launcher) }.collect { (prefs, decks, launcher) ->
                _state.update {
                    it.copy(
                        gateEnabled = prefs.gateEnabled,
                        cooldownMinutes = prefs.cooldownMinutes,
                        hasPinSet = prefs.adminPinHash.isNotBlank(),
                        disableUntilEpoch = prefs.disableUntilEpoch,
                        activeDeckId = prefs.activeDeckId,
                        decks = decks,
                        // Skip PIN gate if no PIN set. Progress is launched directly (no PIN,
                        // matching its old behavior as a tap target on Home's stat pills) and
                        // must never be auto-redirected away by this recomputation.
                        screen = when {
                            _state.value.screen == SettingsScreen.PROGRESS -> SettingsScreen.PROGRESS
                            prefs.adminPinHash.isBlank() -> SettingsScreen.MAIN
                            else -> _state.value.screen
                        },
                        iconShape = launcher.iconShape,
                        iconSizeScale = launcher.iconSizeScale,
                        showIconLabels = launcher.showIconLabels,
                        notificationBadgesEnabled = launcher.notificationBadgesEnabled,
                        notificationBadgeShowCount = launcher.notificationBadgeShowCount
                    )
                }
            }
        }
        refreshNotificationAccessState()
    }

    /** Re-checks whether the OS currently grants this app notification-listener access. Call from onResume. */
    fun refreshNotificationAccessState() {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        _state.update { it.copy(notificationAccessGranted = granted) }
    }

    fun setNotificationBadgesEnabled(enabled: Boolean) {
        viewModelScope.launch { launcherPrefs.setNotificationBadgesEnabled(enabled) }
    }

    fun setNotificationBadgeShowCount(showCount: Boolean) {
        viewModelScope.launch { launcherPrefs.setNotificationBadgeShowCount(showCount) }
    }

    fun setIconShape(shape: IconShape) {
        viewModelScope.launch { launcherPrefs.setIconShape(shape) }
    }

    fun setIconSizeScale(scale: Float) {
        viewModelScope.launch { launcherPrefs.setIconSizeScale(scale) }
    }

    fun setShowIconLabels(show: Boolean) {
        viewModelScope.launch { launcherPrefs.setShowIconLabels(show) }
    }

    fun verifyPin(enteredPin: String) {
        viewModelScope.launch {
            val hash = prefsStore.prefsFlow.first().adminPinHash
            if (hash.isBlank() || BCrypt.checkpw(enteredPin, hash)) {
                _state.update { it.copy(pinVerified = true, pinError = false, screen = SettingsScreen.MAIN) }
            } else {
                _state.update { it.copy(pinError = true) }
            }
        }
    }

    fun setPin(newPin: String) {
        viewModelScope.launch {
            val hash = if (newPin.isBlank()) "" else BCrypt.hashpw(newPin, BCrypt.gensalt())
            prefsStore.setAdminPinHash(hash)
        }
    }

    fun setGateEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setGateEnabled(enabled) }
    }

    fun setCooldown(minutes: Int) {
        viewModelScope.launch { prefsStore.setCooldownMinutes(minutes) }
    }

    fun pauseGate(hours: Float) {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + (hours * 3_600_000).toLong()
            prefsStore.setDisableUntilEpoch(until)
        }
    }

    fun setActiveDeck(deckId: String) {
        viewModelScope.launch { prefsStore.setActiveDeckId(deckId) }
    }

    fun navigateTo(screen: SettingsScreen) {
        _state.update { it.copy(screen = screen) }
    }

    fun navigateBack() {
        val current = _state.value.screen
        val target = when (current) {
            SettingsScreen.DISABLE_PICKER -> SettingsScreen.MAIN
            SettingsScreen.DECK_MANAGER   -> SettingsScreen.MAIN
            SettingsScreen.QUESTION_EDITOR -> SettingsScreen.DECK_MANAGER
            else -> current
        }
        _state.update { it.copy(screen = target) }
    }

    fun editDeck(deckId: String) {
        _state.update { it.copy(editingDeckId = deckId, screen = SettingsScreen.QUESTION_EDITOR) }
    }
}
