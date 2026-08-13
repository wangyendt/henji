package com.qingheng.weight.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingheng.weight.HengJiApp
import com.qingheng.weight.data.*
import com.qingheng.weight.health.HealthPermissionState
import com.qingheng.weight.health.HealthSyncScheduler
import com.qingheng.weight.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HengJiApp
    private var openedSyncCompleted = false
    val weights = app.repository.weightRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val meals = app.repository.mealRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mealFoodItems = app.repository.mealFoodItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foodFrequencies = app.repository.foodFrequencies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wellness = app.repository.wellnessRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dailyBriefings = app.repository.dailyBriefings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val isSaving = MutableStateFlow(false)
    val fitdaysImportState = MutableStateFlow<FitdaysImportState>(FitdaysImportState.Idle)
    val healthSyncState = MutableStateFlow<HealthSyncState>(HealthSyncState.Checking)

    fun addManualWeight(weight: Double) = viewModelScope.launch {
        val weightKg = settings.value.weightUnit.toKilograms(weight)
        if (weightKg in 5.0..350.0) app.repository.saveManualWeight(weightKg, settings.value.profile)
    }

    fun importFitdaysHistory(uri: Uri) = viewModelScope.launch {
        fitdaysImportState.value = FitdaysImportState.Importing
        fitdaysImportState.value = runCatching {
            val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                FitdaysHistoryImporter(getApplication<Application>().contentResolver).read(uri)
            }
            if (parsed.records.isEmpty()) error("文件中没有可导入的称重记录")
            FitdaysImportState.Success(app.repository.importFitdaysHistory(parsed))
        }.getOrElse { error ->
            FitdaysImportState.Error(error.message ?: "导入失败，请重新从 Fitdays 导出")
        }
    }

    fun healthPermissionsToRequest(): Set<String> = app.healthConnectSync.permissionsToRequest()

    fun refreshHealthConnect(autoSync: Boolean = true) = viewModelScope.launch {
        val permissionResult = runCatching { app.healthConnectSync.permissionState() }
        val permission = permissionResult.getOrNull()
        if (permission == null) {
            healthSyncState.value = HealthSyncState.Error(
                permissionResult.exceptionOrNull()?.message ?: "Health Connect 状态读取失败",
                false,
            )
            if (autoSync) catchUpYesterdayBriefing()
            return@launch
        }
        when {
            !permission.available -> healthSyncState.value = HealthSyncState.Unavailable
            !permission.anyHealthGranted -> healthSyncState.value = HealthSyncState.PermissionRequired
            autoSync -> syncHealthConnect(permission)
            else -> healthSyncState.value = HealthSyncState.Ready(permission.backgroundGranted)
        }
        if (autoSync) catchUpYesterdayBriefing()
    }

    fun onHealthPermissionsResult() = viewModelScope.launch {
        val permission = app.healthConnectSync.permissionState()
        if (permission.backgroundGranted) HealthSyncScheduler.schedule(getApplication())
        if (permission.anyHealthGranted) syncHealthConnect(permission)
        else healthSyncState.value = HealthSyncState.PermissionRequired
        catchUpYesterdayBriefing(forceCheck = true)
    }

    fun syncHealthConnectNow() = viewModelScope.launch {
        val permission = app.healthConnectSync.permissionState()
        if (permission.anyHealthGranted) syncHealthConnect(permission)
        else healthSyncState.value = HealthSyncState.PermissionRequired
        catchUpYesterdayBriefing(forceCheck = true)
    }

    private suspend fun syncHealthConnect(permission: HealthPermissionState) {
        if (healthSyncState.value is HealthSyncState.Syncing) return
        healthSyncState.value = HealthSyncState.Syncing
        healthSyncState.value = runCatching {
            val result = app.healthConnectSync.sync(app.settings.values.first().profile)
            HealthSyncState.Success(
                recordsRead = result.recordsRead,
                recordsChanged = result.recordsChanged,
                wellnessDaysChanged = result.wellnessDaysChanged,
                backgroundEnabled = permission.backgroundGranted,
                sleepEnabled = permission.sleepGranted,
                activityEnabled = permission.activityGranted,
            )
        }.getOrElse {
            HealthSyncState.Error(it.message ?: "自动同步失败", true)
        }
    }

    fun updateProfile(profile: UserProfile) = viewModelScope.launch { app.settings.updateProfile(profile) }
    fun updateService(url: String, token: String) = viewModelScope.launch { app.settings.updateService(url, token) }
    fun toggleWeightUnit() = viewModelScope.launch {
        app.settings.updateWeightUnit(settings.value.weightUnit.other())
    }
    fun toggleWeightVisibility() = viewModelScope.launch {
        app.settings.updateHideAbsoluteWeight(!settings.value.hideAbsoluteWeight)
    }
    fun deleteWeight(record: WeightRecord) = viewModelScope.launch { app.repository.deleteWeight(record) }
    fun deleteMeal(record: MealRecord) = viewModelScope.launch { app.repository.deleteMeal(record) }
    fun deleteMeals(records: List<MealRecord>) = viewModelScope.launch { app.repository.deleteMeals(records) }
    suspend fun saveMeal(record: MealRecord, foodItems: List<MealFoodItem>) = app.repository.saveMeal(record, foodItems)
    fun generateYesterdayBriefing() = HealthSyncScheduler.runBriefingNow(getApplication())

    private suspend fun catchUpYesterdayBriefing(forceCheck: Boolean = false) {
        if (openedSyncCompleted && !forceCheck) return
        openedSyncCompleted = true
        val date = LocalDate.now().minusDays(1)
        val yesterday = date.toEpochDay()
        val briefing = app.repository.findBriefing(yesterday)
        val wellness = app.repository.findWellness(yesterday)
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val mealCount = app.repository.mealRecords.first().count { it.createdAt in start until end }
        val stale = briefing == null || briefing.mealCount != mealCount ||
            (wellness != null && briefing.generatedAt < wellness.syncedAt)
        if (stale) HealthSyncScheduler.runBriefingNow(getApplication())
    }

}

sealed interface FitdaysImportState {
    data object Idle : FitdaysImportState
    data object Importing : FitdaysImportState
    data class Success(val summary: FitdaysImportSummary) : FitdaysImportState
    data class Error(val message: String) : FitdaysImportState
}

sealed interface HealthSyncState {
    data object Checking : HealthSyncState
    data object Unavailable : HealthSyncState
    data object PermissionRequired : HealthSyncState
    data object Syncing : HealthSyncState
    data class Ready(val backgroundEnabled: Boolean) : HealthSyncState
    data class Success(
        val recordsRead: Int,
        val recordsChanged: Int,
        val wellnessDaysChanged: Int,
        val backgroundEnabled: Boolean,
        val sleepEnabled: Boolean,
        val activityEnabled: Boolean,
    ) : HealthSyncState
    data class Error(val message: String, val permissionGranted: Boolean) : HealthSyncState
}
