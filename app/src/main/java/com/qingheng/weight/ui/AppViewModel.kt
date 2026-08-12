package com.qingheng.weight.ui

import android.app.Application
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
    private var lastSavedSignature: String? = null

    init {
        viewModelScope.launch { settings.collect { ble.updateProfile(it.profile) } }
        viewModelScope.launch {
            ble.measurement.filterNotNull().filter { it.isStable }.collect { metrics ->
                val signature = "${metrics.weightKg}:${metrics.impedanceOhm}:${metrics.rawPacketHex}"
                if (signature != lastSavedSignature) {
                    lastSavedSignature = signature
                    val deviceName = (ble.state.value as? com.qingheng.weight.ble.BleState.Connected)?.name ?: "蓝牙体脂秤"
                    app.repository.saveMeasurement(metrics, deviceName)
                }
            }
        }
    }

    fun startScan() = ble.startScan()
    fun connect(device: ScaleDevice) {
        viewModelScope.launch { app.settings.selectDevice(device.address) }
        ble.connect(device)
    }

    fun addManualWeight(weight: Double) = viewModelScope.launch {
        if (weight in 5.0..350.0) app.repository.saveManualWeight(weight, settings.value.profile)
    }

    fun updateProfile(profile: UserProfile) = viewModelScope.launch { app.settings.updateProfile(profile) }
    fun updateService(url: String, token: String) = viewModelScope.launch { app.settings.updateService(url, token) }
    fun deleteWeight(record: WeightRecord) = viewModelScope.launch { app.repository.deleteWeight(record) }
    fun deleteMeal(record: MealRecord) = viewModelScope.launch { app.repository.deleteMeal(record) }
    suspend fun saveMeal(record: MealRecord) = app.repository.saveMeal(record)

    override fun onCleared() { ble.disconnect(); super.onCleared() }
}

