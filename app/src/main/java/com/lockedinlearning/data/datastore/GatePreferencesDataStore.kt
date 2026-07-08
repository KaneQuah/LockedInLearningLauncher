package com.lockedinlearning.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.gateDataStore: DataStore<Preferences> by preferencesDataStore(name = "gate_prefs")

data class GatePreferences(
    val gateEnabled: Boolean = true,
    val cooldownMinutes: Int = 3,
    val adminPinHash: String = "",
    val disableUntilEpoch: Long = 0L,
    val activeDeckId: String = "",
    val dailyGoal: Int = 10,
    val onboardingComplete: Boolean = false
)

@Singleton
class GatePreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GATE_ENABLED = booleanPreferencesKey("gate_enabled")
        val COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val ADMIN_PIN_HASH = stringPreferencesKey("admin_pin_hash")
        val DISABLE_UNTIL_EPOCH = longPreferencesKey("disable_until_epoch")
        val ACTIVE_DECK_ID = stringPreferencesKey("active_deck_id")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val prefsFlow: Flow<GatePreferences> = context.gateDataStore.data.map { prefs ->
        GatePreferences(
            gateEnabled = prefs[Keys.GATE_ENABLED] ?: true,
            cooldownMinutes = prefs[Keys.COOLDOWN_MINUTES] ?: 3,
            adminPinHash = prefs[Keys.ADMIN_PIN_HASH] ?: "",
            disableUntilEpoch = prefs[Keys.DISABLE_UNTIL_EPOCH] ?: 0L,
            activeDeckId = prefs[Keys.ACTIVE_DECK_ID] ?: "",
            dailyGoal = prefs[Keys.DAILY_GOAL] ?: 10,
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false
        )
    }

    suspend fun setGateEnabled(enabled: Boolean) = context.gateDataStore.edit {
        it[Keys.GATE_ENABLED] = enabled
    }

    suspend fun setCooldownMinutes(minutes: Int) = context.gateDataStore.edit {
        it[Keys.COOLDOWN_MINUTES] = minutes
    }

    suspend fun setAdminPinHash(hash: String) = context.gateDataStore.edit {
        it[Keys.ADMIN_PIN_HASH] = hash
    }

    suspend fun setDisableUntilEpoch(epochMillis: Long) = context.gateDataStore.edit {
        it[Keys.DISABLE_UNTIL_EPOCH] = epochMillis
    }

    suspend fun setActiveDeckId(id: String) = context.gateDataStore.edit {
        it[Keys.ACTIVE_DECK_ID] = id
    }

    suspend fun setDailyGoal(goal: Int) = context.gateDataStore.edit {
        it[Keys.DAILY_GOAL] = goal
    }

    suspend fun setOnboardingComplete(complete: Boolean) = context.gateDataStore.edit {
        it[Keys.ONBOARDING_COMPLETE] = complete
    }
}
