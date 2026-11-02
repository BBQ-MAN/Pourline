package com.timemoredot.ble.demo

import android.content.Context
import android.content.SharedPreferences
import com.timemoredot.ble.AndroidBleEnvironment
import com.timemoredot.ble.BleEnvironment
import com.timemoredot.ble.BleScaleClient
import com.timemoredot.ble.BleScanner
import com.timemoredot.ble.DeviceStore
import com.timemoredot.ble.GattEvent
import com.timemoredot.ble.GattSpec
import com.timemoredot.ble.GattTransport
import com.timemoredot.ble.GattTransportFactory
import com.timemoredot.ble.ScanHit
import com.timemoredot.ble.androidLog
import com.timemoredot.protocol.Advertisement
import com.timemoredot.protocol.EXPECTED_MODEL
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ProtocolConfig
import com.timemoredot.protocol.SCALE_MANUFACTURER_ID
import com.timemoredot.protocol.SCALE_SERVICE_UUID
import com.timemoredot.protocol.ScaleClient
import com.timemoredot.protocol.demo.DemoLinkEvent
import com.timemoredot.protocol.demo.DemoScaleDevice
import com.timemoredot.protocol.demo.DemoScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** debug 소스셋에만 있다. :app debug가 VariantHooks에서만 부른다(architecture.md 10.1.3). */
object DemoScale {
    /** 데모 모드 ScaleClient: 실제 BleScaleClient(내부 생성자) + 데모 transport·스캐너·환경·기기 저장소("scale_demo"). debugFrameLog = true. */
    fun client(context: Context, scope: CoroutineScope, clock: MonotonicClock): ScaleClient {
        val app = context.applicationContext
        return build(scope, clock, AndroidBleEnvironment(app), DemoDeviceStore(app.getSharedPreferences("scale_demo", Context.MODE_PRIVATE)), androidLog(enabled = true))
    }

    /** Context 없는 JVM 테스트용(:core:ble·:app test): 권한 허용·위치 켜짐 고정, 기기 저장은 메모리, 로그는 버림. */
    fun clientForTest(scope: CoroutineScope, clock: MonotonicClock): ScaleClient = build(scope, clock, null, DemoDeviceStore(null)) {}

    private fun build(scope: CoroutineScope, clock: MonotonicClock, system: BleEnvironment?, store: DeviceStore, logSink: (String) -> Unit): ScaleClient {
        val env = DemoEnvironment(scope, system)
        return BleScaleClient(scope, DemoTransportFactory(scope, clock, env), clock, ProtocolConfig.Default, true, env, DemoScanner(scope), store, logSink)
    }
}

/** 300 ms 뒤 DemoScenario 6개를 광고 하나씩으로 낸다(isScaleCandidate (b)를 지난다). 콜백은 scope 디스패처. */
internal class DemoScanner(private val scope: CoroutineScope) : BleScanner {
    private var job: Job? = null

    override fun start(onResult: (ScanHit) -> Unit, onFailed: (Int) -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(300)
            DemoScenario.entries.forEachIndexed { i, s ->
                onResult(ScanHit(s.address, -40 - 5 * i, Advertisement(s.displayName, emptyList(), mapOf(SCALE_MANUFACTURER_ID to byteArrayOf(0x02, 0x01)))))
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}

/** 권한·위치는 실제 환경(system, null이면 허용·켜짐 고정), 블루투스는 데모 값. */
internal class DemoEnvironment(private val scope: CoroutineScope, private val system: BleEnvironment?) : BleEnvironment {
    private var on = true
    private var listener: ((Boolean) -> Unit)? = null

    override fun hasPermissions(): Boolean = system?.hasPermissions() ?: true
    override fun isBluetoothOn(): Boolean = on
    override fun isLocationOffForScan(): Boolean = system?.isLocationOffForScan() ?: false
    override fun observeBluetooth(onChange: (on: Boolean) -> Unit) { listener = onChange }

    /** 꺼짐 → durationMs 뒤 켜짐. BleScaleClient의 실제 꺼짐·켜짐 처리를 지난다. */
    fun bluetoothOff(durationMs: Long) {
        set(false)
        scope.launch {
            delay(durationMs)
            set(true)
        }
    }

    private fun set(value: Boolean) {
        on = value
        listener?.invoke(value)
    }
}

/** 주소마다 DemoScaleDevice 하나를 들고 있다(연결이 끊겨도 저울 상태 유지). */
internal class DemoTransportFactory(
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val env: DemoEnvironment,
) : GattTransportFactory {
    private val devices = mutableMapOf<String, DemoScaleDevice>()

    override fun connect(address: String): GattTransport {
        val device = DemoScenario.byAddress(address)?.let { s -> devices.getOrPut(s.address) { DemoScaleDevice(s, clock.nowNanos()) } }
        return DemoGattTransport(device, scope, clock, env::bluetoothOff)
    }
}

/** device가 null(데모 주소가 아님)이면 ConnectionChanged(false, 133)만 낸다. */
internal class DemoGattTransport(
    private val device: DemoScaleDevice?,
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val onBluetoothOff: (Long) -> Unit,
) : GattTransport {
    private val channel = Channel<GattEvent>(Channel.UNLIMITED)
    override val events: Flow<GattEvent> = channel.receiveAsFlow()
    private var closed = false
    private var polling: Job? = null

    init {
        val ok = device?.onConnectAttempt(clock.nowNanos()) == true
        channel.trySend(GattEvent.ConnectionChanged(ok, if (ok) 0 else GATT_ERROR))
    }

    override suspend fun discoverServices(): Boolean = !closed
    override suspend fun requestMtu(mtu: Int): Int? = if (closed) null else GattSpec.REQUEST_MTU

    override suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean {
        val d = device
        if (closed || d == null || service != SCALE_SERVICE_UUID || characteristic != GattSpec.UPLINK) return false
        d.onConnected(clock.nowNanos())
        polling = scope.launch { pollLoop(d) }
        return true
    }

    private suspend fun pollLoop(d: DemoScaleDevice) {
        while (true) {
            delay(POLL_MS)
            val p = d.poll(clock.nowNanos())
            p.notifications.forEach { channel.trySend(GattEvent.Notification(GattSpec.UPLINK, it, clock.nowNanos())) }
            when (val e = p.link) {
                is DemoLinkEvent.Drop -> { channel.trySend(GattEvent.ConnectionChanged(false, e.status)); return }
                is DemoLinkEvent.BluetoothOff -> { onBluetoothOff(e.durationMs); return }
                null -> Unit
            }
        }
    }

    override suspend fun writeNoResponse(service: UUID, characteristic: UUID, value: ByteArray): Boolean {
        if (closed || service != SCALE_SERVICE_UUID || characteristic != GattSpec.DOWNLINK) return false
        device?.onWrite(value.copyOf(), clock.nowNanos())
        return true
    }

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray? = when {
        closed || service != GattSpec.DIS_SERVICE -> null
        characteristic == GattSpec.MODEL_NUMBER -> EXPECTED_MODEL.encodeToByteArray()
        characteristic == GattSpec.FIRMWARE_REVISION -> "demo".encodeToByteArray()   // S3에서 데모임이 보인다
        else -> null
    }

    override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean =
        service == SCALE_SERVICE_UUID && (characteristic == GattSpec.UPLINK || characteristic == GattSpec.DOWNLINK)

    override fun close() {
        if (closed) return
        closed = true
        polling?.cancel()
        channel.close()
    }

    private companion object {
        const val GATT_ERROR = 133
        const val POLL_MS = 100L
    }
}

/** prefs가 null이면 메모리에만 둔다. 이름 규칙은 SharedPrefsDeviceStore와 같다. */
internal class DemoDeviceStore(private val prefs: SharedPreferences?) : DeviceStore {
    override var lastAddress: String? = prefs?.getString(KEY_ADDRESS, null)
        private set
    override var lastName: String? = prefs?.getString(KEY_NAME, null)
        private set

    override fun save(address: String, name: String?) {
        if (name != null) lastName = name else if (address != lastAddress) lastName = null
        lastAddress = address
        prefs?.edit()?.putString(KEY_ADDRESS, address)?.putString(KEY_NAME, lastName)?.apply()
    }

    private companion object {
        const val KEY_ADDRESS = "last_address"
        const val KEY_NAME = "last_name"
    }
}
