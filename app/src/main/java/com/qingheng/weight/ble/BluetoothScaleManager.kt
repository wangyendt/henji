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
        val FFE3: UUID = uuid("FFE3"); val FFE4: UUID = uuid("FFE4")
        val FFF0: UUID = uuid("FFF0"); val FFF1: UUID = uuid("FFF1")
        val FFF2: UUID = uuid("FFF2")
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
    private val writeQueue = ArrayDeque<Pair<BluetoothGattCharacteristic, ByteArray>>()
    private var writeInProgress = false
    private var qnProtocolType: Byte = 0

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

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInProgress = false
            if (status != BluetoothGatt.GATT_SUCCESS) log("写入 ${characteristic.uuid.toString().substring(4, 8)} 失败：$status")
            writeNext(g)
        }

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
                when (data.firstOrNull()?.u()) {
                    0x12 -> if (data.size > 10) {
                        qnProtocolType = data[2]
                        scaleFactor = if (data[10].u() == 1) 100.0 else 10.0
                        sendQnConfiguration()
                    }
                    0x14 -> sendQnTimeAck()
                }
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

    private fun sendQnConfiguration() {
        val g = gatt ?: return
        val shared = findCharacteristic(g, FFF2)
        val configTarget = shared ?: findCharacteristic(g, FFE3)
        val timeTarget = shared ?: findCharacteristic(g, FFE4)
        if (configTarget == null) { log("QN 配置通道不存在，将继续被动接收数据"); return }

        val config = byteArrayOf(0x13, 0x09, qnProtocolType, 0x01, 0x10, 0x00, 0x00, 0x00, 0x00)
        config[config.lastIndex] = config.dropLast(1).sumOf { it.u() }.toByte()
        queueWrite(configTarget, config)

        if (timeTarget != null) {
            val secondsSince2000 = (System.currentTimeMillis() / 1000L - 946_702_800L).toInt()
            queueWrite(timeTarget, byteArrayOf(
                0x02, secondsSince2000.toByte(), (secondsSince2000 ushr 8).toByte(),
                (secondsSince2000 ushr 16).toByte(), (secondsSince2000 ushr 24).toByte(),
            ))
        }
        writeNext(g)
        log("已同步 QN/Fitdays 秤的单位和时间")
    }

    private fun sendQnTimeAck() {
        val g = gatt ?: return
        val target = findCharacteristic(g, FFF2) ?: findCharacteristic(g, FFE3) ?: return
        val seconds = (System.currentTimeMillis() / 1000L - 946_702_800L).toInt()
        val packet = byteArrayOf(
            0x20, 0x08, qnProtocolType, seconds.toByte(), (seconds ushr 8).toByte(),
            (seconds ushr 16).toByte(), (seconds ushr 24).toByte(), 0,
        )
        packet[packet.lastIndex] = packet.dropLast(1).sumOf { it.u() }.toByte()
        queueWrite(target, packet); writeNext(g)
    }

    private fun findCharacteristic(g: BluetoothGatt, id: UUID): BluetoothGattCharacteristic? =
        g.services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull { it.uuid == id }

    private fun queueWrite(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        writeQueue.add(characteristic to data)
    }

    private fun writeNext(g: BluetoothGatt) {
        if (writeInProgress) return
        val (characteristic, data) = writeQueue.removeFirstOrNull() ?: return
        writeInProgress = true
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val started = if (android.os.Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.value = data
            g.writeCharacteristic(characteristic)
        }
        if (!started) { writeInProgress = false; log("无法启动 Fitdays 配置写入"); writeNext(g) }
    }
}
