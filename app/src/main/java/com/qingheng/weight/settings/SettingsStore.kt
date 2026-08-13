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

data class AppSettings(
    val profile: UserProfile = UserProfile(),
    val serviceUrl: String = "http://10.0.2.2:7777",
    val serviceToken: String = "",
    val personalSyncUrl: String = "http://100.84.108.13:8787",
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
        val personalSyncUrl = stringPreferencesKey("personal_sync_url")
        val personalSyncToken = stringPreferencesKey("personal_sync_token")
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
            personalSyncUrl = p[Keys.personalSyncUrl] ?: "http://100.84.108.13:8787",
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

    suspend fun updateService(url: String, token: String) = context.dataStore.edit {
        it[Keys.serviceUrl] = url.trimEnd('/'); it[Keys.serviceToken] = token
    }

    suspend fun updatePersonalSync(url: String, token: String) = context.dataStore.edit {
        it[Keys.personalSyncUrl] = url.trimEnd('/')
        it[Keys.personalSyncToken] = token
    }

    suspend fun updateWeightUnit(unit: WeightUnit) = context.dataStore.edit {
        it[Keys.weightUnit] = unit.name
    }

    suspend fun updateHideAbsoluteWeight(hidden: Boolean) = context.dataStore.edit {
        it[Keys.hideAbsoluteWeight] = hidden
    }
}
