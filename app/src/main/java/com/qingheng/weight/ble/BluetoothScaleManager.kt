package com.qingheng.weight.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.qingheng.weight.data.BodyMetrics
import com.qingheng.weight.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ScaleDevice(val address: String, val name: String, val rssi: Int, val protocol: String)
sealed interface BleState {
    data object Idle : BleState
    data object Scanning : BleState
    data class Connecting(val name: String) : BleState
    data class Connected(val name: String) : BleState
    data class Error(val message: String) : BleState
}

@SuppressLint("MissingPermission")
class BluetoothScaleManager(context: Context) {
    companion object {
        private fun uuid(short: String) = UUID.fromString("0000${short.lowercase()}-0000-1000-8000-00805f9b34fb")
        val WEIGHT_SERVICE: UUID = uuid("181D"); val WEIGHT_MEASUREMENT: UUID = uuid("2A9D")
        val BODY_SERVICE: UUID = uuid("181B"); val BODY_MEASUREMENT: UUID = uuid("2A9C")
        val FFE0: UUID = uuid("FFE0"); val FFE1: UUID = uuid("FFE1")
        val FFF0: UUID = uuid("FFF0"); val FFF1: UUID = uuid("FFF1")
        val FFB0: UUID = uuid("FFB0"); val FFB2: UUID = uuid("FFB2"); val FFB3: UUID = uuid("FFB3")
        private val CCCD: UUID = uuid("2902")
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var profile = UserProfile()
    private var scaleFactor = 100.0
    private val assembler = IcomonFrameAssembler()
    private val found = linkedMapOf<String, ScaleDevice>()
    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<ScaleDevice>>(emptyList())
    val devices: StateFlow<List<ScaleDevice>> = _devices.asStateFlow()
    private val _measurement = MutableStateFlow<BodyMetrics?>(null)
    val measurement: StateFlow<BodyMetrics?> = _measurement.asStateFlow()
    private val _packets = MutableStateFlow<List<String>>(emptyList())
    val packets: StateFlow<List<String>> = _packets.asStateFlow()

    fun updateProfile(value: UserProfile) { profile = value }

    fun startScan() {
        if (adapter?.isEnabled != true) { _state.value = BleState.Error("请先开启手机蓝牙"); return }
        found.clear(); _devices.value = emptyList(); _state.value = BleState.Scanning
        scanner = adapter?.bluetoothLeScanner
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
    }

    fun stopScan() { scanner?.stopScan(scanCallback); if (_state.value is BleState.Scanning) _state.value = BleState.Idle }

    fun connect(device: ScaleDevice) {
        stopScan(); gatt?.close(); _state.value = BleState.Connecting(device.name)
        gatt = adapter?.getRemoteDevice(device.address)?.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() { gatt?.disconnect(); gatt?.close(); gatt = null; _state.value = BleState.Idle }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val services = record.serviceUuids?.map(ParcelUuid::getUuid).orEmpty()
            val known = services.any { it in setOf(WEIGHT_SERVICE, BODY_SERVICE, FFE0, FFF0, FFB0) }
            val aabb = record.getManufacturerSpecificData(0xFFFF)?.let { it.size >= 2 && it[0].u() == 0xAA && it[1].u() == 0xBB } == true
            val name = result.device.name ?: record.deviceName ?: "未命名体脂秤"
            if (known || aabb || name.contains("scale", true) || name.contains("icomon", true)) {
                val protocol = when { FFB0 in services -> "Fitdays FFB0"; FFE0 in services || FFF0 in services -> "Fitdays/QN"; aabb -> "Fitdays 广播"; else -> "标准 BLE" }
                found[result.device.address] = ScaleDevice(result.device.address, name, result.rssi, protocol)
                _devices.value = found.values.sortedByDescending { it.rssi }
            }
            record.getServiceData(ParcelUuid(WEIGHT_SERVICE))?.let { publish(ScaleProtocol.parseXiaomiWeight(it, profile), name) }
            record.getServiceData(ParcelUuid(BODY_SERVICE))?.let { publish(ScaleProtocol.parseXiaomiBody(it, profile), name) }
            record.getManufacturerSpecificData(0xFFFF)?.let { publish(ScaleProtocol.parseQnBroadcast(it, profile), name) }
        }

        override fun onScanFailed(errorCode: Int) { _state.value = BleState.Error("扫描失败（$errorCode）") }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = BleState.Connected(g.device.name ?: "体脂秤"); g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = if (status == BluetoothGatt.GATT_SUCCESS) BleState.Idle else BleState.Error("连接已断开（$status）")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { _state.value = BleState.Error("服务发现失败（$status）"); return }
            notifyQueue.clear()
            g.services.flatMap { it.characteristics }.filter {
                it.uuid in setOf(WEIGHT_MEASUREMENT, BODY_MEASUREMENT, FFE1, FFF1, FFB2, FFB3) ||
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }.forEach(notifyQueue::add)
            enableNextNotification(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = enableNextNotification(g)

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) =
            handle(characteristic.uuid, characteristic.value, g.device.name)

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
            handle(characteristic.uuid, value, g.device.name)
    }

    private fun enableNextNotification(g: BluetoothGatt) {
        val c = notifyQueue.removeFirstOrNull() ?: return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD)
        if (d == null) { enableNextNotification(g); return }
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (android.os.Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) else { d.value = value; g.writeDescriptor(d) }
    }

    private fun handle(uuid: UUID, data: ByteArray, name: String?) {
        log("${uuid.toString().substring(4, 8).uppercase()}: ${data.hex()}")
        val parsed = when (uuid) {
            WEIGHT_MEASUREMENT -> ScaleProtocol.parseStandardWeight(data, profile)
            BODY_MEASUREMENT -> ScaleProtocol.parseStandardBody(data, profile)
            FFE1, FFF1 -> {
                if (data.firstOrNull()?.u() == 0x12 && data.size > 4) scaleFactor = if (data[4].u() == 10) 10.0 else 100.0
                ScaleProtocol.parseQn(data, scaleFactor, profile)
            }
            FFB2, FFB3 -> assembler.accept(data, profile)
            else -> null
        }
        publish(parsed, name ?: "体脂秤")
    }

    private fun publish(value: BodyMetrics?, name: String) {
        if (value == null) return
        _measurement.value = value
        if (_state.value is BleState.Scanning && value.isStable) log("已从 $name 收到稳定数据")
    }

    private fun log(text: String) { _packets.value = (listOf(text) + _packets.value).take(30) }
}

