package com.qingheng.weight.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingheng.weight.QingHengApp
import com.qingheng.weight.ble.BluetoothScaleManager
import com.qingheng.weight.ble.ScaleDevice
import com.qingheng.weight.data.*
import com.qingheng.weight.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QingHengApp
    val ble = BluetoothScaleManager(application)
    val weights = app.repository.weightRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val meals = app.repository.mealRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val isSaving = MutableStateFlow(false)
    val fitdaysImportState = MutableStateFlow<FitdaysImportState>(FitdaysImportState.Idle)

    init {
        viewModelScope.launch { settings.collect { ble.updateProfile(it.profile) } }
        viewModelScope.launch {
            ble.measurement.filterNotNull().filter { it.isStable }.collect { metrics ->
                val deviceName = (ble.state.value as? com.qingheng.weight.ble.BleState.Connected)?.name ?: "蓝牙体脂秤"
                app.repository.saveMeasurement(metrics, deviceName)
            }
        }
    }

    fun startScan() = ble.startScan(settings.value.selectedDeviceAddress)
    fun connect(device: ScaleDevice) {
        viewModelScope.launch { app.settings.selectDevice(device.address) }
        ble.connect(device)
    }

    fun addManualWeight(weight: Double) = viewModelScope.launch {
        if (weight in 5.0..350.0) app.repository.saveManualWeight(weight, settings.value.profile)
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

    fun updateProfile(profile: UserProfile) = viewModelScope.launch { app.settings.updateProfile(profile) }
    fun updateService(url: String, token: String) = viewModelScope.launch { app.settings.updateService(url, token) }
    fun deleteWeight(record: WeightRecord) = viewModelScope.launch { app.repository.deleteWeight(record) }
    fun deleteMeal(record: MealRecord) = viewModelScope.launch { app.repository.deleteMeal(record) }
    suspend fun saveMeal(record: MealRecord) = app.repository.saveMeal(record)

    override fun onCleared() { ble.disconnect(); super.onCleared() }
}

sealed interface FitdaysImportState {
    data object Idle : FitdaysImportState
    data object Importing : FitdaysImportState
    data class Success(val summary: FitdaysImportSummary) : FitdaysImportState
    data class Error(val message: String) : FitdaysImportState
}
