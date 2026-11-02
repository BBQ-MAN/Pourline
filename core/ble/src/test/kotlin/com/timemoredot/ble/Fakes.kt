package com.timemoredot.ble

import com.timemoredot.protocol.Advertisement
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.EXPECTED_MODEL
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ProtocolConfig
import com.timemoredot.protocol.ResponseKey
import com.timemoredot.protocol.SCALE_MANUFACTURER_ID
import com.timemoredot.protocol.SCALE_SERVICE_UUID
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.responseKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

/** docs/spec/protocol-vectors.json(경로는 build.gradle.kts 시스템 속성). 값을 테스트에 복사하지 않고 id로 읽는다. */
object BleVectors {
    private val all: List<JsonObject> by lazy {
        val path = System.getProperty("protocol.vectors") ?: error("protocol.vectors 시스템 속성 없음")
        Json.parseToJsonElement(File(path).readText()).jsonObject.getValue("vectors").jsonArray.map { it.jsonObject }
    }
    fun byId(id: String): JsonObject = all.single { it.getValue("id").jsonPrimitive.content == id }
    fun hex(id: String): String = byId(id).getValue("hex").jsonPrimitive.content
    /** 결합 벡터 expect 배열의 index번째 객체 */
    fun expect(id: String, index: Int): JsonObject = byId(id).getValue("expect").jsonArray[index].jsonObject
}

val G7_SERVICE: UUID = UUID.fromString("5833ff01-9b8b-5191-6142-22a4536ef123")   // protocol.md G-7 (테스트 전용, main에는 두지 않는다)

/** 호출 기록용 짧은 이름: 0000fff1-… → fff1, 5833ff01-… → 5833ff01 */
fun UUID.short(): String = toString().substring(0, 8).trimStart('0')

/** GattTransport 가짜. 실제와 같은 GattOpSlot으로 콜백을 맞춘다. held에 든 종류는 콜백을 자동으로 보내지 않는다(callback()으로 직접 보낸다). */
internal class FakeGattTransport(val address: String, private val clock: MonotonicClock) : GattTransport {
    val calls = mutableListOf<String>()
    var closeCalls = 0
        private set
    var releases = 0
        private set
    private var closed = false
    private val channel = Channel<GattEvent>(Channel.UNLIMITED)
    override val events: Flow<GattEvent> = channel.receiveAsFlow()
    private val slot = GattOpSlot()

    val services = mutableMapOf(
        SCALE_SERVICE_UUID to mutableSetOf(GattSpec.UPLINK, GattSpec.DOWNLINK),
        GattSpec.DIS_SERVICE to mutableSetOf(GattSpec.MODEL_NUMBER, GattSpec.FIRMWARE_REVISION),
    )
    var discoverOk = true
    var mtuResult: Int? = 247
    var notifyOk = true
    val reads = mutableMapOf<UUID, ByteArray?>(
        GattSpec.MODEL_NUMBER to EXPECTED_MODEL.encodeToByteArray(),
        GattSpec.FIRMWARE_REVISION to "1.0.4".encodeToByteArray(),
    )
    val held = mutableSetOf<GattOpKind>()
    var securityOn: String? = null                     // 이 이름으로 시작하는 호출에서 SecurityException
    var errorOn: Pair<String, RuntimeException>? = null  // 이 이름으로 시작하는 호출에서 그 예외
    var responder: (ByteArray) -> List<String> = ::defaultResponse   // FFF2 쓰기 완료 뒤 보낼 알림(hex)
    var leakEventsAfterClose = false                   // true면 close()가 채널을 닫지 않는다(버린 transport의 늦은 이벤트 흉내)

    val writes: List<String> get() = calls.filter { it.startsWith("write") }

    fun connected() = channel.trySend(GattEvent.ConnectionChanged(true, 0))
    fun drop(status: Int) = channel.trySend(GattEvent.ConnectionChanged(false, status))
    fun notify(hex: String) = channel.trySend(GattEvent.Notification(GattSpec.UPLINK, hex.hexToByteArray(), clock.nowNanos()))
    fun callback(kind: GattOpKind, value: Any?): Boolean = slot.complete(kind, value)

    private suspend fun op(kind: GattOpKind, call: String, value: Any?): Any? {
        securityOn?.let { if (call.startsWith(it)) throw SecurityException(call) }
        errorOn?.let { (prefix, e) -> if (call.startsWith(prefix)) throw e }
        calls += call
        return slot.run(kind) {
            if (kind !in held) slot.complete(kind, value)
            !closed
        }
    }

    override suspend fun discoverServices(): Boolean = op(GattOpKind.DiscoverServices, "discoverServices", discoverOk.takeIf { it }) == true
    override suspend fun requestMtu(mtu: Int): Int? = op(GattOpKind.Mtu, "requestMtu($mtu)", mtuResult) as Int?
    override suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean =
        op(GattOpKind.DescriptorWrite, "enableNotifications(${service.short()},${characteristic.short()})", notifyOk.takeIf { it }) == true

    override suspend fun writeNoResponse(service: UUID, characteristic: UUID, value: ByteArray): Boolean {
        val ok = op(GattOpKind.CharacteristicWrite, "write(${service.short()},${characteristic.short()},${value.toHexString(HexFormat.UpperCase)})", true) == true
        if (ok) responder(value).forEach(::notify)
        return ok
    }

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray? =
        op(GattOpKind.CharacteristicRead, "read(${service.short()},${characteristic.short()})", reads[characteristic]) as ByteArray?

    override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = services[service]?.contains(characteristic) == true

    override fun close() {
        closeCalls++
        if (closed) return
        closed = true
        releases++
        slot.close()
        if (!leakEventsAfterClose) channel.close()
    }

    companion object {
        fun keyOf(frame: ByteArray) = ResponseKey(frame[2].toInt() and 0xFF, frame[3].toInt() and 0xFF)

        /** 연결 직후 읽기(GATT-1)에 실측 결합 알림으로 답한다. */
        fun defaultResponse(frame: ByteArray): List<String> = when (keyOf(frame)) {
            ScaleCommand.ReadUnit.responseKey() -> listOf(BleVectors.hex("rx-unit-read-002"))
            ScaleCommand.ReadBattery.responseKey() -> listOf(BleVectors.hex("rx-battery-read-002"))
            ScaleCommand.ReadTimer.responseKey() -> listOf(BleVectors.hex("rx-timer-read-002"))
            else -> emptyList()
        }

        /** defaultResponse에 명령 하나의 응답을 더한다. */
        fun respond(command: ScaleCommand, vararg hex: String): (ByteArray) -> List<String> = { frame ->
            if (keyOf(frame) == command.responseKey()) hex.toList() else defaultResponse(frame)
        }
    }
}

internal class FakeEnvironment : BleEnvironment {
    var permissions = true
    var btOn = true
    var locationOff = false
    private var listener: ((Boolean) -> Unit)? = null
    override fun hasPermissions() = permissions
    override fun isBluetoothOn() = btOn
    override fun isLocationOffForScan() = locationOff
    override fun observeBluetooth(onChange: (on: Boolean) -> Unit) { listener = onChange }
    fun setBluetooth(on: Boolean) {
        btOn = on
        listener!!(on)
    }
}

internal class FakeScanner(private val nowMs: () -> Long) : BleScanner {
    val startTimesMs = mutableListOf<Long>()
    var running = false
        private set
    var throwSecurity = false
    private var onResult: ((ScanHit) -> Unit)? = null
    private var onFailed: ((Int) -> Unit)? = null

    override fun start(onResult: (ScanHit) -> Unit, onFailed: (Int) -> Unit) {
        if (throwSecurity) throw SecurityException("scan")
        startTimesMs += nowMs()
        running = true
        this.onResult = onResult
        this.onFailed = onFailed
    }

    override fun stop() { running = false }

    fun hit(address: String, name: String?, mfr: ByteArray? = byteArrayOf(0x01, 0x01), uuids: List<UUID> = emptyList(), rssi: Int = -50) =
        onResult!!(ScanHit(address, rssi, Advertisement(name, uuids, mfr?.let { mapOf(SCALE_MANUFACTURER_ID to it) }.orEmpty())))

    fun fail(code: Int) = onFailed!!(code)
}

internal class FakeStore(override var lastAddress: String? = null, override var lastName: String? = null) : DeviceStore {
    override fun save(address: String, name: String?) {
        if (name != null) lastName = name else if (address != lastAddress) lastName = null
        lastAddress = address
    }
}

const val ADDR_A = "AA:BB:CC:DD:EE:01"
const val ADDR_B = "AA:BB:CC:DD:EE:02"
const val MS = 1_000_000L

/** BleScaleClient + 가짜들. 시각은 가상 시간(ms) × 1e6 ns. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class Harness(val test: TestScope, debugFrameLog: Boolean = true) {
    val clock = MonotonicClock { test.testScheduler.currentTime * MS }
    val env = FakeEnvironment()
    val scanner = FakeScanner { test.testScheduler.currentTime }
    val store = FakeStore()
    val logs = mutableListOf<String>()
    val transports = mutableListOf<FakeGattTransport>()
    var throwOnConnect = false
    var connectError: RuntimeException? = null
    /** 새 transport가 생기면 부른다. 기본: 바로 연결됨. */
    var onCreate: (FakeGattTransport) -> Unit = { it.connected() }
    private val factory = GattTransportFactory { address ->
        if (throwOnConnect) throw SecurityException("connectGatt")
        connectError?.let { throw it }
        FakeGattTransport(address, clock).also { transports += it; onCreate(it) }
    }
    val client by lazy {
        BleScaleClient(test.backgroundScope, factory, clock, ProtocolConfig.Default, debugFrameLog, env, scanner, store, logs::add)
    }
    val states = mutableListOf<ConnectionState>()

    val last: FakeGattTransport get() = transports.last()
    val state: ConnectionState get() = client.connection.value

    fun record() {
        test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { client.connection.toList(states) }
    }

    fun advance(ms: Long) {
        test.advanceTimeBy(ms)
        test.runCurrent()
    }

    /** connect(ADDR_A) 뒤 GATT-1 끝까지 진행해 Connected(disReadDone=true)에 이른다. */
    fun connectA(): FakeGattTransport {
        client.connect(ADDR_A)
        test.runCurrent()
        val s = state
        check(s is ConnectionState.Connected && s.device.disReadDone) { "연결 안 됨: $s" }
        return last
    }
}
