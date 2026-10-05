package com.qingheng.weight.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.qingheng.weight.data.ActivityLevel
import com.qingheng.weight.data.Sex
import com.qingheng.weight.data.UserProfile
import com.qingheng.weight.data.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

internal const val DEFAULT_PERSONAL_SYNC_URL = "https://wangye.xin/services/henji-sync"
internal const val LEGACY_PERSONAL_SYNC_URL = "http://100.84.108.13:8787"
private const val PERSONAL_SYNC_URL_MIGRATION_VERSION = 1

data class AppSettings(
    val profile: UserProfile = UserProfile(),
    val serviceUrl: String = "http://10.0.2.2:7777",
    val serviceToken: String = "",
    val serviceModel: String = "",
    val serviceReasoning: String = "",
    val personalSyncUrl: String = DEFAULT_PERSONAL_SYNC_URL,
    val personalSyncToken: String = "",
    val weightUnit: WeightUnit = WeightUnit.KILOGRAM,
    val hideAbsoluteWeight: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val height = intPreferencesKey("height")
        val birthYear = intPreferencesKey("birth_year")
        val sex = stringPreferencesKey("sex")
        val activity = stringPreferencesKey("activity")
        val goalWeight = doublePreferencesKey("goal_weight")
        val serviceUrl = stringPreferencesKey("service_url")
        val serviceToken = stringPreferencesKey("service_token")
        val serviceModel = stringPreferencesKey("service_model")
        val serviceReasoning = stringPreferencesKey("service_reasoning")
        val personalSyncUrl = stringPreferencesKey("personal_sync_url")
        val personalSyncToken = stringPreferencesKey("personal_sync_token")
        val personalSyncUrlMigrationVersion = intPreferencesKey("personal_sync_url_migration_version")
        val weightUnit = stringPreferencesKey("weight_unit")
        val hideAbsoluteWeight = booleanPreferencesKey("hide_absolute_weight")
    }

    val values: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            profile = UserProfile(
                heightCm = p[Keys.height] ?: 170,
                birthYear = p[Keys.birthYear] ?: 1990,
                sex = runCatching { Sex.valueOf(p[Keys.sex] ?: "MALE") }.getOrDefault(Sex.MALE),
                activityLevel = runCatching { ActivityLevel.valueOf(p[Keys.activity] ?: "MODERATE") }.getOrDefault(ActivityLevel.MODERATE),
                goalWeightKg = p[Keys.goalWeight] ?: 65.0,
            ),
            serviceUrl = p[Keys.serviceUrl] ?: "http://10.0.2.2:7777",
            serviceToken = p[Keys.serviceToken] ?: "",
            serviceModel = p[Keys.serviceModel] ?: "",
            serviceReasoning = p[Keys.serviceReasoning] ?: "",
            personalSyncUrl = resolvePersonalSyncUrl(
                storedUrl = p[Keys.personalSyncUrl],
                migrationVersion = p[Keys.personalSyncUrlMigrationVersion] ?: 0,
            ),
            personalSyncToken = p[Keys.personalSyncToken] ?: "",
            weightUnit = runCatching {
                WeightUnit.valueOf(p[Keys.weightUnit] ?: WeightUnit.KILOGRAM.name)
            }.getOrDefault(WeightUnit.KILOGRAM),
            hideAbsoluteWeight = p[Keys.hideAbsoluteWeight] ?: false,
        )
    }

    suspend fun updateProfile(profile: UserProfile) = context.dataStore.edit {
        it[Keys.height] = profile.heightCm; it[Keys.birthYear] = profile.birthYear
        it[Keys.sex] = profile.sex.name; it[Keys.activity] = profile.activityLevel.name
        it[Keys.goalWeight] = profile.goalWeightKg
    }

    suspend fun updateService(url: String, token: String, model: String = "", reasoning: String = "") = context.dataStore.edit {
        it[Keys.serviceUrl] = url.trimEnd('/'); it[Keys.serviceToken] = token.trim()
        it[Keys.serviceModel] = model.trim(); it[Keys.serviceReasoning] = reasoning.trim()
    }

    suspend fun updatePersonalSync(url: String, token: String) = context.dataStore.edit {
        it[Keys.personalSyncUrl] = url.trimEnd('/')
        it[Keys.personalSyncToken] = token
    }

    suspend fun migratePersonalSyncUrl() = context.dataStore.edit {
        if ((it[Keys.personalSyncUrlMigrationVersion] ?: 0) >= PERSONAL_SYNC_URL_MIGRATION_VERSION) {
            return@edit
        }
        val storedUrl = it[Keys.personalSyncUrl]?.trimEnd('/')
        if (storedUrl.isNullOrBlank() || storedUrl == LEGACY_PERSONAL_SYNC_URL) {
            it[Keys.personalSyncUrl] = DEFAULT_PERSONAL_SYNC_URL
        }
        it[Keys.personalSyncUrlMigrationVersion] = PERSONAL_SYNC_URL_MIGRATION_VERSION
    }

    suspend fun updateWeightUnit(unit: WeightUnit) = context.dataStore.edit {
        it[Keys.weightUnit] = unit.name
    }

    suspend fun updateHideAbsoluteWeight(hidden: Boolean) = context.dataStore.edit {
        it[Keys.hideAbsoluteWeight] = hidden
    }
}

internal fun resolvePersonalSyncUrl(storedUrl: String?, migrationVersion: Int): String {
    val normalized = storedUrl?.trimEnd('/')
    return when {
        normalized.isNullOrBlank() -> DEFAULT_PERSONAL_SYNC_URL
        migrationVersion < PERSONAL_SYNC_URL_MIGRATION_VERSION && normalized == LEGACY_PERSONAL_SYNC_URL -> {
            DEFAULT_PERSONAL_SYNC_URL
        }
        else -> normalized
    }
}
