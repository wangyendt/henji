package com.qingheng.weight.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingheng.weight.HengJiApp
import com.qingheng.weight.data.*
import com.qingheng.weight.health.HealthPermissionState
import com.qingheng.weight.health.HealthSyncScheduler
import com.qingheng.weight.meal.CodexTaskClient
import com.qingheng.weight.settings.AppSettings
import com.qingheng.weight.sync.PersonalSyncResult
import com.qingheng.weight.sync.PersonalSyncScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HengJiApp
    val weights = app.repository.weightRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val meals = app.repository.mealRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mealFoodItems = app.repository.mealFoodItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foodFrequencies = app.repository.foodFrequencies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wellnessRecords = app.repository.wellnessRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val syncPendingCount = app.personalSync.pendingCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val isSaving = MutableStateFlow(false)
    val fitdaysImportState = MutableStateFlow<FitdaysImportState>(FitdaysImportState.Idle)
    val healthSyncState = MutableStateFlow<HealthSyncState>(HealthSyncState.Checking)
    val personalSyncState = MutableStateFlow<PersonalSyncState>(PersonalSyncState.Checking)
    val healthScreenshotImportState = MutableStateFlow<HealthScreenshotImportState>(HealthScreenshotImportState.Idle)

    fun addManualWeight(weight: Double) = viewModelScope.launch {
        val weightKg = settings.value.weightUnit.toKilograms(weight)
        if (weightKg in 5.0..350.0) {
            app.repository.saveManualWeight(weightKg, settings.value.profile)
            PersonalSyncScheduler.enqueueNow(getApplication())
        }
    }

    fun importFitdaysHistory(uri: Uri) = viewModelScope.launch {
        fitdaysImportState.value = FitdaysImportState.Importing
        fitdaysImportState.value = runCatching {
            val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                FitdaysHistoryImporter(getApplication<Application>().contentResolver).read(uri)
            }
            if (parsed.records.isEmpty()) error("文件中没有可导入的称重记录")
            FitdaysImportState.Success(app.repository.importFitdaysHistory(parsed)).also {
                PersonalSyncScheduler.enqueueNow(getApplication())
            }
        }.getOrElse { error ->
            FitdaysImportState.Error(error.message ?: "导入失败，请重新从 Fitdays 导出")
        }
    }

    fun importVivoHealthScreenshot(uri: Uri) = viewModelScope.launch {
        if (healthScreenshotImportState.value is HealthScreenshotImportState.Analyzing) return@launch
        val current = app.settings.values.first()
        if (current.serviceToken.isBlank()) {
            healthScreenshotImportState.value = HealthScreenshotImportState.Error(
                "请先到“我的”中填写 CodexTask Service Token",
            )
            return@launch
        }
        healthScreenshotImportState.value = HealthScreenshotImportState.Analyzing
        healthScreenshotImportState.value = runCatching {
            val analysis = CodexTaskClient(current.serviceUrl, current.serviceToken)
                .analyzeHealthScreenshot(getApplication<Application>().contentResolver, uri)
            val records = analysis.toRecords()
            val summary = app.repository.saveWellnessRecords(records)
            val syncResult = if (current.personalSyncUrl.isNotBlank() && current.personalSyncToken.isNotBlank()) {
                runCatching { app.personalSync.sync(current.personalSyncUrl, current.personalSyncToken) }
            } else null
            if (syncResult?.isFailure == true) PersonalSyncScheduler.enqueueNow(getApplication())
            HealthScreenshotImportState.Success(
                summary = summary,
                records = records,
                warnings = analysis.warnings,
                uploaded = syncResult?.isSuccess == true,
                syncMessage = syncResult?.exceptionOrNull()?.message,
            )
        }.getOrElse { error ->
            HealthScreenshotImportState.Error(error.message ?: "健康截图识别失败，请稍后重试")
        }
    }

    fun dismissHealthScreenshotImport() {
        healthScreenshotImportState.value = HealthScreenshotImportState.Idle
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
            HealthSyncState.Success(
                recordsRead = result.recordsRead,
                recordsChanged = result.recordsChanged,
                backgroundEnabled = permission.backgroundGranted,
            )
        }.getOrElse {
            HealthSyncState.Error(it.message ?: "自动同步失败", true)
        }
        syncPersonalDataInternal()
    }

    fun updateProfile(profile: UserProfile) = viewModelScope.launch { app.settings.updateProfile(profile) }
    fun updateService(url: String, token: String) = viewModelScope.launch { app.settings.updateService(url, token) }
    fun updatePersonalSync(url: String, token: String) = viewModelScope.launch {
        app.settings.updatePersonalSync(url, token)
        syncPersonalDataInternal()
        PersonalSyncScheduler.enqueueNow(getApplication())
    }
    fun syncPersonalData() = viewModelScope.launch { syncPersonalDataInternal() }
    fun toggleWeightUnit() = viewModelScope.launch {
        app.settings.updateWeightUnit(settings.value.weightUnit.other())
    }
    fun toggleWeightVisibility() = viewModelScope.launch {
        app.settings.updateHideAbsoluteWeight(!settings.value.hideAbsoluteWeight)
    }
    fun deleteWeight(record: WeightRecord) = viewModelScope.launch {
        app.repository.deleteWeight(record)
        PersonalSyncScheduler.enqueueNow(getApplication())
    }
    fun deleteMeal(record: MealRecord) = viewModelScope.launch {
        app.repository.deleteMeal(record)
        PersonalSyncScheduler.enqueueNow(getApplication())
    }
    fun deleteMeals(records: List<MealRecord>) = viewModelScope.launch {
        app.repository.deleteMeals(records)
        PersonalSyncScheduler.enqueueNow(getApplication())
    }
    fun deleteWellness(record: DailyWellnessRecord) = viewModelScope.launch {
        app.repository.deleteWellness(record)
        PersonalSyncScheduler.enqueueNow(getApplication())
    }
    suspend fun saveMeal(record: MealRecord, foodItems: List<MealFoodItem>) {
        app.repository.saveMeal(record, foodItems)
        PersonalSyncScheduler.enqueueNow(getApplication())
    }

    private suspend fun syncPersonalDataInternal() {
        val current = app.settings.values.first()
        if (current.personalSyncUrl.isBlank() || current.personalSyncToken.isBlank()) {
            personalSyncState.value = PersonalSyncState.Disabled
            return
        }
        personalSyncState.value = PersonalSyncState.Syncing
        personalSyncState.value = runCatching {
            PersonalSyncState.Success(app.personalSync.sync(current.personalSyncUrl, current.personalSyncToken))
        }.getOrElse { PersonalSyncState.Error(it.message ?: "个人数据同步失败") }
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
        val backgroundEnabled: Boolean,
    ) : HealthSyncState
    data class Error(val message: String, val permissionGranted: Boolean) : HealthSyncState
}

sealed interface PersonalSyncState {
    data object Checking : PersonalSyncState
    data object Disabled : PersonalSyncState
    data object Syncing : PersonalSyncState
    data class Success(val result: PersonalSyncResult) : PersonalSyncState
    data class Error(val message: String) : PersonalSyncState
}

sealed interface HealthScreenshotImportState {
    data object Idle : HealthScreenshotImportState
    data object Analyzing : HealthScreenshotImportState
    data class Success(
        val summary: WellnessImportSummary,
        val records: List<DailyWellnessRecord>,
        val warnings: List<String>,
        val uploaded: Boolean,
        val syncMessage: String?,
    ) : HealthScreenshotImportState
    data class Error(val message: String) : HealthScreenshotImportState
}
