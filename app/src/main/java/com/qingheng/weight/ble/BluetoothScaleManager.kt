package com.qingheng.weight.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
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
        val FFB0: UUID = uuid("FFB0"); val FFB1: UUID = uuid("FFB1")
        val FFB2: UUID = uuid("FFB2"); val FFB3: UUID = uuid("FFB3")
        val FFB4: UUID = uuid("FFB4"); val FFB5: UUID = uuid("FFB5")
        private val CCCD: UUID = uuid("2902")
    }

    private data class QueuedWrite(
        val characteristic: BluetoothGattCharacteristic,
        val data: ByteArray,
        val preferNoResponse: Boolean = false,
    )

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var profile = UserProfile()
    private var scaleFactor = 100.0
    private val icomonAssemblers = mutableMapOf<UUID, IcomonFrameAssembler>()
    private val icomonAdvertisementAssembler = IcomonFrameAssembler()
    private val icomonCommands = IcomonCommandSession()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val found = linkedMapOf<String, ScaleDevice>()
    private val scannedDevices = mutableMapOf<String, BluetoothDevice>()
    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private val writeQueue = ArrayDeque<QueuedWrite>()
    private var writeInProgress = false
    private var activeWriteNoResponse = false
    private var writeGeneration = 0
    private var qnProtocolType: Byte = 0
    private var notificationsReady = false
    private var icomonV3 = false
    private var waitingForMtu = false
    private var icomonSessionActive = false
    private var icomonProfilePublished = false
    private var serviceDiscoveryAttempts = 0
    private var lastAdvertisementHex: String? = null
    private var automaticMode = false
    private var connectScheduled = false
    private var connectionAttempt = 0

    private val icomonHeartbeat = object : Runnable {
        override fun run() {
            sendIcomonHeartbeat()
            if (icomonSessionActive) mainHandler.postDelayed(this, 400)
        }
    }

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<ScaleDevice>>(emptyList())
    val devices: StateFlow<List<ScaleDevice>> = _devices.asStateFlow()
    private val _measurement = MutableStateFlow<BodyMetrics?>(null)
    val measurement: StateFlow<BodyMetrics?> = _measurement.asStateFlow()
    private val _packets = MutableStateFlow<List<String>>(emptyList())
    val packets: StateFlow<List<String>> = _packets.asStateFlow()

    fun updateProfile(value: UserProfile) {
        if (profile != value) icomonProfilePublished = false
        profile = value
    }

    fun startScan(preferredAddress: String? = null) {
        automaticMode = true
        connectionAttempt = 0
        beginScan(clearHistory = true)
        if (preferredAddress != null) {
            val preferred = ScaleDevice(preferredAddress, "icomon", Int.MIN_VALUE, "Fitdays FFB0")
            mainHandler.post { if (automaticMode && _state.value is BleState.Scanning) connectNow(preferred, waitForWake = true) }
        }
    }

    private fun beginScan(clearHistory: Boolean) {
        if (adapter?.isEnabled != true) { _state.value = BleState.Error("请先开启手机蓝牙"); return }
        scanner?.stopScan(scanCallback)
        found.clear(); _devices.value = emptyList()
        if (clearHistory) _packets.value = emptyList()
        lastAdvertisementHex = null; _state.value = BleState.Scanning
        scanner = adapter?.bluetoothLeScanner
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        log(if (clearHistory) "持续扫描已开启，体脂秤亮起后会立即自动连接" else "继续等待体脂秤广播…")
    }

    fun stopScan() { scanner?.stopScan(scanCallback); if (_state.value is BleState.Scanning) _state.value = BleState.Idle }

    fun connect(device: ScaleDevice) {
        automaticMode = true
        connectNow(device)
    }

    private fun connectNow(device: ScaleDevice, waitForWake: Boolean = false) {
        connectScheduled = false
        if (!waitForWake) stopScan()
        stopIcomonSession(); gatt?.close()
        serviceDiscoveryAttempts = 0
        connectionAttempt += 1
        _measurement.value = null; _state.value = BleState.Connecting(device.name)
        log(if (waitForWake) "已提前等待体脂秤唤醒，亮起后会直接连接" else "检测到 ${device.name}，正在自动连接（第 $connectionAttempt 次）")
        // Match the connection overload used by ICOMON's Android SDK. The explicit transport
        // overload returns an empty GATT table on some Vivo/Android 16 combinations.
        val bluetoothDevice = scannedDevices[device.address] ?: adapter?.getRemoteDevice(device.address)
        gatt = bluetoothDevice?.connectGatt(appContext, waitForWake, gattCallback)
    }

    fun disconnect() {
        automaticMode = false
        connectScheduled = false
        stopIcomonSession(); gatt?.disconnect(); gatt?.close(); gatt = null; _state.value = BleState.Idle
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val services = record.serviceUuids?.map(ParcelUuid::getUuid).orEmpty()
            val known = services.any { it in setOf(WEIGHT_SERVICE, BODY_SERVICE, FFE0, FFF0, FFB0) }
            val aabb = record.getManufacturerSpecificData(0xFFFF)?.let { it.size >= 2 && it[0].u() == 0xAA && it[1].u() == 0xBB } == true
            val name = result.device.name ?: record.deviceName ?: "未命名体脂秤"
            if (known || aabb || name.contains("scale", true) || name.contains("icomon", true)) {
                val protocol = when { FFB0 in services -> "Fitdays FFB0"; FFE0 in services || FFF0 in services -> "Fitdays/QN"; aabb -> "Fitdays 广播"; else -> "标准 BLE" }
                val device = ScaleDevice(result.device.address, name, result.rssi, protocol)
                scannedDevices[result.device.address] = result.device
                found[result.device.address] = device
                _devices.value = found.values.sortedByDescending { it.rssi }
                if (FFB0 in services || name.contains("icomon", true)) {
                    val rawHex = record.bytes.hex()
                    if (rawHex != lastAdvertisementHex) {
                        lastAdvertisementHex = rawHex
                        log("ADV（可连接=${result.isConnectable}）: $rawHex")
                    }
                    parseIcomonAdvertisement(record, name)
                }
                if (automaticMode && result.isConnectable && !connectScheduled && _state.value is BleState.Scanning) {
                    connectScheduled = true
                    mainHandler.post {
                        if (automaticMode && _state.value is BleState.Scanning) connectNow(device)
                        else connectScheduled = false
                    }
                }
            }
            record.getServiceData(ParcelUuid(WEIGHT_SERVICE))?.let { publish(ScaleProtocol.parseXiaomiWeight(it, profile), name) }
            record.getServiceData(ParcelUuid(BODY_SERVICE))?.let { publish(ScaleProtocol.parseXiaomiBody(it, profile), name) }
            record.getManufacturerSpecificData(0xFFFF)?.let { publish(ScaleProtocol.parseQnBroadcast(it, profile), name) }
        }

        override fun onScanFailed(errorCode: Int) { _state.value = BleState.Error("扫描失败（$errorCode）") }
    }

    private fun parseIcomonAdvertisement(record: ScanRecord, name: String) {
        val candidates = buildList {
            record.getServiceData(ParcelUuid(FFB0))?.let(::add)
            val manufacturerData = record.manufacturerSpecificData
            for (i in 0 until manufacturerData.size()) {
                val id = manufacturerData.keyAt(i)
                val value = byteArrayOf(id.toByte(), (id ushr 8).toByte()) + manufacturerData.valueAt(i)
                add(value)
            }
            add(record.bytes)
        }
        for (candidate in candidates) {
            val frames = buildList {
                add(candidate)
                for (offset in candidate.indices) {
                    if (offset + 6 <= candidate.size && candidate[offset + 3].u() == 0) {
                        val compactSize = candidate[offset + 2].u() + 5
                        if (compactSize >= 6 && offset + compactSize <= candidate.size) {
                            add(candidate.sliceArray(offset until offset + compactSize))
                        }
                    }
                    if (offset + 20 <= candidate.size) add(candidate.sliceArray(offset until offset + 20))
                }
            }
            for (frame in frames) {
                val value = icomonAdvertisementAssembler.accept(frame, profile) ?: continue
                publish(value, name)
                return
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scanner?.stopScan(scanCallback)
                _state.value = BleState.Connected(g.device.name ?: "体脂秤")
                serviceDiscoveryAttempts = 0
                // This scale exposes its table only when discovery starts in the connection
                // callback. Posting it even 50 ms later consistently yields an empty table.
                discoverScaleServices(g)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stopIcomonSession()
                if (automaticMode) scheduleRescan(g, "连接中断（$status）")
                else _state.value = if (status == BluetoothGatt.GATT_SUCCESS) BleState.Idle else BleState.Error("连接已断开（$status）")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (automaticMode) scheduleRescan(g, "服务发现失败（$status）")
                else _state.value = BleState.Error("服务发现失败（$status）")
                return
            }
            notifyQueue.clear(); notificationsReady = false
            val allCharacteristics = g.services.flatMap { it.characteristics }
            if (allCharacteristics.isEmpty() && automaticMode && serviceDiscoveryAttempts < 5) {
                log("服务表暂时为空，保持连接重读（$serviceDiscoveryAttempts/5）")
                mainHandler.postDelayed({ discoverScaleServices(g) }, 250)
                return
            }
            if (allCharacteristics.isEmpty() && automaticMode) {
                scheduleRescan(g, "连续 5 次服务表为空")
                return
            }
            icomonV3 = g.getService(FFB0) != null && allCharacteristics.any { it.uuid == FFB4 || it.uuid == FFB5 }
            val notificationOrder = when {
                icomonV3 -> listOf(FFB3, FFB2)
                g.getService(FFB0) != null -> listOf(FFB2)
                else -> listOf(WEIGHT_MEASUREMENT, BODY_MEASUREMENT, FFE1, FFF1)
            }
            notificationOrder.mapNotNull { id -> allCharacteristics.firstOrNull { it.uuid == id } }
                .forEach(notifyQueue::add)
            log("GATT 共 ${g.services.size} 个服务、${allCharacteristics.size} 个特征；发现 ${notifyQueue.size} 个数据通道：${notifyQueue.joinToString { it.uuid.shortName() }}")
            waitingForMtu = icomonV3 && g.requestMtu(512)
            if (waitingForMtu) log("正在为 Fitdays V3 数据申请大包通道") else enableNextNotification(g)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g !== gatt || !waitingForMtu) return
            waitingForMtu = false
            log(if (status == BluetoothGatt.GATT_SUCCESS) "Fitdays V3 数据通道 MTU=$mtu" else "大包通道申请失败（$status），继续兼容模式")
            enableNextNotification(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt) return
            val id = descriptor.characteristic.uuid.shortName()
            log(if (status == BluetoothGatt.GATT_SUCCESS) "已订阅 $id" else "订阅 $id 失败：$status")
            enableNextNotification(g)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            if (activeWriteNoResponse) return
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

    private fun scheduleRescan(g: BluetoothGatt, reason: String) {
        if (g !== gatt || !automaticMode) return
        log("$reason，将自动重新等待体脂秤亮起")
        gatt = null
        stopIcomonSession()
        runCatching { g.disconnect() }
        g.close()
        _state.value = BleState.Scanning
        mainHandler.postDelayed({ if (automaticMode && gatt == null) beginScan(clearHistory = false) }, 250)
    }

    private fun discoverScaleServices(g: BluetoothGatt) {
        if (g !== gatt) return
        serviceDiscoveryAttempts += 1
        if (!g.discoverServices()) {
            log("第 $serviceDiscoveryAttempts 次服务读取未启动")
            if (serviceDiscoveryAttempts < 5) {
                mainHandler.postDelayed({ discoverScaleServices(g) }, 250)
            } else if (automaticMode) {
                scheduleRescan(g, "连续 5 次服务读取未启动")
            }
        }
    }

    private fun enableNextNotification(g: BluetoothGatt) {
        val c = notifyQueue.removeFirstOrNull() ?: run { onNotificationsReady(g); return }
        if (!g.setCharacteristicNotification(c, true)) {
            log("启用 ${c.uuid.shortName()} 通知失败")
            enableNextNotification(g)
            return
        }
        val d = c.getDescriptor(CCCD)
        if (d == null) { enableNextNotification(g); return }
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val started = if (android.os.Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = value
            g.writeDescriptor(d)
        }
        if (!started) {
            log("订阅 ${c.uuid.shortName()} 未启动，继续下一个通道")
            mainHandler.post { enableNextNotification(g) }
        }
    }

    private fun onNotificationsReady(g: BluetoothGatt) {
        if (notificationsReady) return
        notificationsReady = true
        log("体脂秤数据通道已就绪")
        if (icomonV3) {
            // Device subtype 12 uses WeightGeneralV2. Fitdays only negotiates MTU and subscribes
            // FFB3 followed by FFB2; sending ScaleNew's AC setup suppresses the final A3 result.
            log("已启动 Fitdays V3 体重与阻抗会话")
        } else if (findCharacteristic(g, FFB1) != null && findCharacteristic(g, FFB2) != null) {
            startIcomonSession(g)
        }
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
            FFB2, FFB3 -> {
                icomonAssemblers.getOrPut(uuid, ::IcomonFrameAssembler)
                    .accept(data, profile, _measurement.value?.weightKg)
            }
            else -> null
        }
        publish(parsed, name ?: "体脂秤")
    }

    private fun publish(value: BodyMetrics?, name: String) {
        if (value == null) return
        _measurement.value = value
        if (_state.value is BleState.Scanning && value.isStable) log("已从 $name 收到稳定数据")
    }

    private fun log(text: String) {
        Log.d("QingHengBle", text)
        _packets.value = (listOf(text) + _packets.value).take(100)
    }

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

    private fun startIcomonSession(g: BluetoothGatt) {
        val target = findCharacteristic(g, FFB1) ?: return
        mainHandler.removeCallbacks(icomonHeartbeat)
        icomonSessionActive = false
        icomonProfilePublished = false
        icomonCommands.reset()
        icomonAssemblers.clear()
        icomonSessionActive = true
        icomonProfilePublished = false

        // Match ICOMON ScaleNew exactly: after FFB2 is subscribed, wait 100 ms, then write
        // its 8-byte unit and time commands to FFB1 with a response.
        mainHandler.postDelayed({
            if (!icomonSessionActive || g !== gatt) return@postDelayed
            icomonCommands.scaleNewSetup().forEach { queueWrite(target, it) }
            writeNext(g)
            log("已启动 Fitdays 实时称重会话")
        }, 120)
    }

    private fun stopIcomonSession(clearWrites: Boolean = true) {
        icomonSessionActive = false
        icomonProfilePublished = false
        notificationsReady = false
        waitingForMtu = false
        mainHandler.removeCallbacks(icomonHeartbeat)
        icomonAssemblers.clear()
        if (clearWrites) {
            writeQueue.clear()
            writeInProgress = false
            activeWriteNoResponse = false
            writeGeneration += 1
        }
    }

    private fun sendIcomonHeartbeat() {
        if (!icomonSessionActive) return
        val g = gatt ?: return
        val target = findCharacteristic(g, FFB1) ?: return
        if (writeQueue.size > 4) return
        val value = _measurement.value
        val weight = value?.weightKg ?: 0.0
        icomonCommands.sync(profile, weight, stabilized = value?.isStable == true)
            .forEach { queueWrite(target, it, preferNoResponse = true) }
        writeNext(g)
    }

    private fun publishIcomonProfile(value: BodyMetrics) {
        if (!icomonSessionActive || value.weightKg <= 0.0) return
        val g = gatt ?: return
        val target = findCharacteristic(g, FFB1) ?: return
        icomonProfilePublished = true
        icomonCommands.userList(profile, value.weightKg, stabilized = value.isStable)
            .forEach { queueWrite(target, it, preferNoResponse = true) }
        icomonCommands.other().forEach { queueWrite(target, it, preferNoResponse = true) }
        writeNext(g)
        log("已把用户资料同步给体脂秤")
    }

    private fun sendIcomonAck() {
        if (!icomonSessionActive) return
        val g = gatt ?: return
        val target = findCharacteristic(g, FFB1) ?: return
        icomonCommands.ack().forEach { queueWrite(target, it, preferNoResponse = true) }
        writeNext(g)
    }

    private fun findCharacteristic(g: BluetoothGatt, id: UUID): BluetoothGattCharacteristic? =
        g.services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull { it.uuid == id }

    private fun queueWrite(
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        preferNoResponse: Boolean = false,
    ) {
        writeQueue.add(QueuedWrite(characteristic, data, preferNoResponse))
    }

    private fun writeNext(g: BluetoothGatt) {
        if (writeInProgress) return
        val request = writeQueue.removeFirstOrNull() ?: return
        val characteristic = request.characteristic
        val data = request.data
        val supportsNoResponse = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val supportsResponse = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val writeType = if ((request.preferNoResponse || !supportsResponse) && supportsNoResponse)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        writeInProgress = true
        activeWriteNoResponse = writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val generation = ++writeGeneration
        characteristic.writeType = writeType
        // Vivo's Android 16 Bluetooth stack rejects the API 33 overload for this legacy FFB1
        // characteristic. Fitdays itself uses this value + writeCharacteristic sequence.
        characteristic.value = data
        val started = g.writeCharacteristic(characteristic)
        if (!started) {
            writeInProgress = false; activeWriteNoResponse = false
            log("无法启动 Fitdays 配置写入"); writeNext(g)
        } else if (activeWriteNoResponse) {
            // Write-without-response has no reliable callback on all Android vendors; pace it here.
            mainHandler.postDelayed({
                if (writeInProgress && activeWriteNoResponse && writeGeneration == generation) {
                    writeInProgress = false
                    activeWriteNoResponse = false
                    writeNext(g)
                }
            }, 80)
        }
    }
}

private fun UUID.shortName(): String = toString().substring(4, 8).uppercase()
