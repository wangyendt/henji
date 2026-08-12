package com.qingheng.weight.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingheng.weight.QingHengApp
import com.qingheng.weight.data.*
import com.qingheng.weight.health.HealthPermissionState
import com.qingheng.weight.health.HealthSyncScheduler
import com.qingheng.weight.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QingHengApp
    val weights = app.repository.weightRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val meals = app.repository.mealRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
        val permission = runCatching { app.healthConnectSync.permissionState() }.getOrElse {
            healthSyncState.value = HealthSyncState.Error(it.message ?: "Health Connect 状态读取失败", false)
            return@launch
        }
        when {
            !permission.available -> healthSyncState.value = HealthSyncState.Unavailable
            !permission.coreGranted -> healthSyncState.value = HealthSyncState.PermissionRequired
            autoSync -> syncHealthConnect(permission)
            else -> healthSyncState.value = HealthSyncState.Ready(permission.backgroundGranted)
        }
    }

    fun onHealthPermissionsResult() = viewModelScope.launch {
        val permission = app.healthConnectSync.permissionState()
        if (permission.backgroundGranted) HealthSyncScheduler.schedule(getApplication())
        if (permission.coreGranted) syncHealthConnect(permission)
        else healthSyncState.value = HealthSyncState.PermissionRequired
    }

    fun syncHealthConnectNow() = viewModelScope.launch {
        val permission = app.healthConnectSync.permissionState()
        if (permission.coreGranted) syncHealthConnect(permission)
        else healthSyncState.value = HealthSyncState.PermissionRequired
    }

    private suspend fun syncHealthConnect(permission: HealthPermissionState) {
        if (healthSyncState.value is HealthSyncState.Syncing) return
        healthSyncState.value = HealthSyncState.Syncing
        healthSyncState.value = runCatching {
            val result = app.healthConnectSync.sync(app.settings.values.first().profile)
            HealthSyncState.Success(result.recordsRead, result.recordsChanged, permission.backgroundGranted)
        }.getOrElse {
            HealthSyncState.Error(it.message ?: "自动同步失败", true)
        }
    }

    fun updateProfile(profile: UserProfile) = viewModelScope.launch { app.settings.updateProfile(profile) }
    fun updateService(url: String, token: String) = viewModelScope.launch { app.settings.updateService(url, token) }
    fun toggleWeightUnit() = viewModelScope.launch {
        app.settings.updateWeightUnit(settings.value.weightUnit.other())
    }
    fun deleteWeight(record: WeightRecord) = viewModelScope.launch { app.repository.deleteWeight(record) }
    fun deleteMeal(record: MealRecord) = viewModelScope.launch { app.repository.deleteMeal(record) }
    suspend fun saveMeal(record: MealRecord) = app.repository.saveMeal(record)

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
    data class Success(val recordsRead: Int, val recordsChanged: Int, val backgroundEnabled: Boolean) : HealthSyncState
    data class Error(val message: String, val permissionGranted: Boolean) : HealthSyncState
}
