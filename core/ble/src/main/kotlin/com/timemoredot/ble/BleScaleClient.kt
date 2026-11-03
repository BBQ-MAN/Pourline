package com.timemoredot.ble

import android.bluetooth.BluetoothGatt
import android.content.Context
import com.timemoredot.protocol.BROADCAST_WHITELIST
import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.DecodeResult
import com.timemoredot.protocol.EncodeResult
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.FrameCodec
import com.timemoredot.protocol.FrameReassembler
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.Op
import com.timemoredot.protocol.ProtocolConfig
import com.timemoredot.protocol.ResponseKey
import com.timemoredot.protocol.SCALE_MANUFACTURER_ID
import com.timemoredot.protocol.SCALE_SERVICE_UUID
import com.timemoredot.protocol.broadcastTypeOf
import com.timemoredot.protocol.ScaleClient
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScaleFrame
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleStateReducer
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.ScanCandidate
import com.timemoredot.protocol.isScaleCandidate
import com.timemoredot.protocol.responseKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/** ScaleClient의 BLE 구현(architecture.md 5.4, 6절). AppContainer가 하나를 소유한다.
 *  공개 함수와 콜백은 scope의 스레드(메인)에서 부른다. 상태는 그 스레드에서만 바꾼다(잠금 없음). send()는 어느 디스패처에서 불러도 scope 디스패처로 옮긴다. */
class BleScaleClient internal constructor(
    private val scope: CoroutineScope,
    private val transportFactory: GattTransportFactory,
    private val clock: MonotonicClock,
    private val config: ProtocolConfig,
    private val debugFrameLog: Boolean,
    private val env: BleEnvironment,
    private val scanner: BleScanner,
    private val store: DeviceStore,
    private val logSink: (String) -> Unit,
) : ScaleClient {

    constructor(
        context: Context,
        scope: CoroutineScope,                 // 앱 범위
        transportFactory: GattTransportFactory,
        clock: MonotonicClock,
        config: ProtocolConfig = ProtocolConfig.Default,
        debugFrameLog: Boolean,                // :app의 BuildConfig.DEBUG를 넘긴다
    ) : this(
        scope, transportFactory, clock, config, debugFrameLog,
        AndroidBleEnvironment(context.applicationContext),
        AndroidBleScanner(context.applicationContext),
        SharedPrefsDeviceStore(context.applicationContext),
        androidLog(enabled = true),
    )

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val _scanResults = MutableStateFlow<List<ScanCandidate>>(emptyList())
    private val reducer = ScaleStateReducer(config)
    private val _status = MutableStateFlow(reducer.status)
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _timerEvents = MutableSharedFlow<ScaleTimerState>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _lastDeviceAddress = MutableStateFlow(store.lastAddress)

    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    override val scanResults: StateFlow<List<ScanCandidate>> = _scanResults.asStateFlow()
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()
    override val timerEvents: SharedFlow<ScaleTimerState> = _timerEvents.asSharedFlow()
    override val lastDeviceAddress: StateFlow<String?> = _lastDeviceAddress.asStateFlow()

    private val dispatcher = scope.coroutineContext[ContinuationInterceptor] ?: EmptyCoroutineContext

    // 연결
    private var session: Job? = null          // 연결 시도·재연결 대기·연결 유지 전체
    private var link: Link? = null            // 현재 transport
    private var connectIntent = false         // 5.4 연결 의도: connect()·connectLastDevice()가 켜고 disconnect()·PowerOff 성공이 끈다. 막힘으로는 끄지 않는다

    // 명령
    private val commandMutex = Mutex()
    private var pending: PendingResponse? = null

    // 스캔
    private var scanJob: Job? = null
    private var scannerRunning = false
    private var lastScanStartNanos: Long? = null
    private val advLogged = mutableSetOf<String>()

    init {
        _connection.value = blockedState(forScan = true) ?: ConnectionState.Idle   // 5.4: 생성 때 블루투스·권한·위치 확인
        env.observeBluetooth(::onBluetoothState)
    }

    private class Link(val address: String, val name: String?, val transport: GattTransport, config: ProtocolConfig) {
        val gattMutex = Mutex()                       // GATT 작업 큐: 한 번에 하나
        val reassembler = FrameReassembler(config)
        val connected = CompletableDeferred<Unit>()
        val end = CompletableDeferred<LinkEnd>()      // 먼저 온 끝 사유가 이긴다
        var mtu = GattSpec.DEFAULT_MTU
        var ready = false                             // 알림 구독까지 끝나 Connected를 냄
        var released = false
        var powerOffAcked = false
        var device: ScaleDevice? = null
    }

    private sealed interface LinkEnd {
        data class Lost(val status: Int) : LinkEnd        // 스택이 알린 끊김
        data class OpFailed(val status: Int) : LinkEnd    // GATT 작업이 실패 status로 끝남
        data object Timeout : LinkEnd                     // 연결 10 s 또는 GATT 작업 5 s 초과
        data object ServiceNotFound : LinkEnd
        data object Permission : LinkEnd
        data object Released : LinkEnd                    // 앱이 닫음(사용자 끊기 등)
    }

    /** sentAtNanos: 쓰기 직전 시각. 그 전에 받은 같은 키 프레임은 다른 앱(central)의 응답이라 짝짓지 않는다(protocol.md 3.13 동시 연결, C-MC-1). */
    private class PendingResponse(val link: Link, val key: ResponseKey) {
        var sentAtNanos = Long.MAX_VALUE                 // GATT 큐를 지나 실제로 쓰기 직전에 찍는다
        val frame = CompletableDeferred<ScaleFrame?>()   // null = 끊김
    }

    private class Box<T>(val value: T)

    // ---- 환경 ----

    override fun refreshEnvironment() {
        val s = _connection.value
        val blocked = blockedState(forScan = true)
        if (blocked != null) {
            if (s is ConnectionState.Idle || s is ConnectionState.Failed || s.isBlocked()) _connection.value = blocked
            return
        }
        if (s.isBlocked()) onUnblocked()
    }

    private fun ConnectionState.isBlocked() =
        this is ConnectionState.BluetoothOff || this is ConnectionState.PermissionMissing || this is ConnectionState.LocationOff

    /** 5.4 순서: BluetoothOff > PermissionMissing > LocationOff. 위치는 스캔에만 필요하다(forScan). */
    private fun blockedState(forScan: Boolean): ConnectionState? = when {
        !env.isBluetoothOn() -> ConnectionState.BluetoothOff
        !env.hasPermissions() -> ConnectionState.PermissionMissing
        forScan && env.isLocationOffForScan() -> ConnectionState.LocationOff
        else -> null
    }

    /** 5.4 막힘 풀림: 연결 의도와 저장 주소가 있으면 재연결 경로(attempt 1, 대기 없음), 아니면 Idle. 첫 연결 규칙을 쓰지 않는다. */
    private fun onUnblocked() {
        val address = store.lastAddress
        if (connectIntent && address != null) startLinkLoop(address, attempt = 1, delayMs = 0) else _connection.value = ConnectionState.Idle
    }

    private fun onBluetoothState(on: Boolean) {
        log { FrameLogFormat.ev("블루투스 ${if (on) "켜짐" else "꺼짐"}") }
        if (!on) {
            stopScanner()
            closeLink()
            _connection.value = ConnectionState.BluetoothOff
            return
        }
        if (_connection.value != ConnectionState.BluetoothOff) return
        blockedState(forScan = true)?.let { _connection.value = it; return }
        onUnblocked()   // 백오프 초기화
    }

    // ---- 스캔 ----

    override fun startScan() {
        val s = _connection.value
        if (s is ConnectionState.Connecting || s is ConnectionState.Connected || s is ConnectionState.Reconnecting) return
        stopScanner()
        blockedState(forScan = true)?.let { _connection.value = it; return }
        _scanResults.value = emptyList()
        advLogged.clear()
        _connection.value = ConnectionState.Scanning
        scanJob = scope.launch {
            // 30 s에 5회 제한(android-ble 2절): 직전 실제 시작 6 s 안이면 늦춘다
            lastScanStartNanos?.let { delay((it + SCAN_RESTART_GAP_NANOS - clock.nowNanos()) / NANOS_PER_MS) }
            lastScanStartNanos = clock.nowNanos()
            try {
                scannerRunning = true
                scanner.start(::onScanHit, ::onScanFailed)
            } catch (e: SecurityException) {
                scannerRunning = false
                _connection.value = ConnectionState.PermissionMissing
                return@launch
            }
            delay(SCAN_DURATION_MS)
            stopScanner()
            _connection.value = if (_scanResults.value.isEmpty()) ConnectionState.Failed(FailureReason.NoDeviceFound) else ConnectionState.Idle
        }
    }

    override fun stopScan() {
        stopScanner()
        if (_connection.value == ConnectionState.Scanning) _connection.value = ConnectionState.Idle
    }

    private fun stopScanner() {
        scanJob?.cancel()
        scanJob = null
        if (!scannerRunning) return
        scannerRunning = false
        try {
            scanner.stop()
        } catch (e: SecurityException) {
            log { FrameLogFormat.ev("stopScan SecurityException") }
        }
    }

    private fun onScanHit(hit: ScanHit) {
        if (!scannerRunning) return
        val adv = hit.advertisement
        if (!isScaleCandidate(adv)) return   // 필터 구현 차이 방어
        if (advLogged.add(hit.address)) {
            log { FrameLogFormat.adv(hit.address, adv.name, adv.manufacturerData[SCALE_MANUFACTURER_ID], adv.serviceUuids) }
        }
        val list = _scanResults.value
        val old = list.firstOrNull { it.address == hit.address }
        // 표시 이름(A-3a): 광고 이름 → 이번 스캔에서 본 이름 → 저장 주소의 last_name → null
        val candidate = ScanCandidate(hit.address, adv.name ?: old?.name ?: savedName(hit.address), hit.rssi, clock.nowNanos(), broadcastTypeOf(adv) ?: old?.broadcastType)   // 제조사 데이터 없는 광고가 앞서 본 방송 유형을 지우지 않게
        _scanResults.value = if (old == null) list + candidate else list.map { if (it.address == hit.address) candidate else it }
    }

    private fun onScanFailed(errorCode: Int) {
        log { FrameLogFormat.ev("스캔 실패 $errorCode") }
        stopScanner()
        _connection.value = ConnectionState.Failed(FailureReason.ScanFailed)
    }

    private fun savedName(address: String): String? = store.lastName.takeIf { address == store.lastAddress }

    private fun nameFor(address: String): String? =
        _scanResults.value.firstOrNull { it.address == address }?.name ?: savedName(address)

    // ---- 연결 ----

    override fun connect(address: String) {
        connectIntent = true
        stopScanner()
        closeLink()
        blockedState(forScan = false)?.let { _connection.value = it; return }
        startLinkLoop(address, attempt = 0, delayMs = 0)
    }

    override fun connectLastDevice(): Boolean {
        val s = _connection.value
        if (s is ConnectionState.Reconnecting) {
            if (clock.nowNanos() < s.nextRetryAtNanos) {   // 백오프 대기 중: 취소하고 지금 시도
                closeLink()
                startLinkLoop(s.address, s.attempt, delayMs = 0)
            }
            return true
        }
        if (s.isBlocked()) {   // 막힘: 연결하지 않고 의도만 켠다. 풀렸으면 refreshEnvironment가 재연결 경로로 보낸다
            connectIntent = true
            refreshEnvironment()
            return true
        }
        connect(store.lastAddress ?: return false)
        return true
    }

    override fun disconnect() {
        connectIntent = false
        stopScanner()
        closeLink()
        _connection.value = blockedState(forScan = false) ?: ConnectionState.Idle
    }

    private fun closeLink() {
        session?.cancel()
        session = null
        link?.let(::release)
    }

    /** attempt 0 = 첫 연결(실패면 Failed). 1..10 = 재연결 시도(실패면 백오프 뒤 다음 시도, 상태는 시도 중에도 Reconnecting). */
    private fun startLinkLoop(address: String, attempt: Int, delayMs: Long) {
        session = scope.launch {
            var n = attempt
            var wait = delayMs
            while (true) {
                _connection.value = if (n == 0) {
                    ConnectionState.Connecting(address, nameFor(address))
                } else {
                    ConnectionState.Reconnecting(address, n, clock.nowNanos() + wait * NANOS_PER_MS)
                }
                delay(wait)
                val (l, end) = runLink(address)
                when {
                    end == LinkEnd.Permission -> { _connection.value = ConnectionState.PermissionMissing; return@launch }
                    l?.ready == true && l.powerOffAcked -> { connectIntent = false; _connection.value = ConnectionState.Idle; return@launch }   // protocol 3.5
                    l?.ready == true -> n = 1
                    n == 0 -> { _connection.value = ConnectionState.Failed(end.toFailure(address)); return@launch }
                    n >= MAX_RECONNECT_ATTEMPTS -> { _connection.value = ConnectionState.Failed(FailureReason.ReconnectExhausted); return@launch }
                    else -> n++
                }
                wait = BACKOFF_MS[minOf(n, BACKOFF_MS.size) - 1]
            }
        }
    }

    private fun LinkEnd.toFailure(address: String): FailureReason = when (this) {
        LinkEnd.Timeout -> FailureReason.ConnectTimeout
        LinkEnd.ServiceNotFound -> FailureReason.ServiceNotFound
        is LinkEnd.Lost -> gattError(status, address)
        is LinkEnd.OpFailed -> gattError(status, address)
        LinkEnd.Permission, LinkEnd.Released -> FailureReason.GattError(BluetoothGatt.GATT_FAILURE)
    }

    /** F-F5-1: 133이고 이 주소의 마지막 광고가 화이트리스트 방송이면 표시한다(스캔 없이 직접 연결이면 모름 = false). */
    private fun gattError(status: Int, address: String) = FailureReason.GattError(
        status,
        whitelistAdvertising = status == GATT_ERROR_133 &&
            _scanResults.value.firstOrNull { it.address == address }?.broadcastType == BROADCAST_WHITELIST,
    )

    /** transport 하나의 수명. 끝나면 항상 close()한다. */
    private suspend fun runLink(address: String): Pair<Link?, LinkEnd> {
        val transport = try {
            transportFactory.connect(address)
        } catch (e: SecurityException) {
            return null to LinkEnd.Permission
        } catch (e: RuntimeException) {   // 잘못된 주소(IllegalArgumentException) 등: 크래시 대신 실패 경로로
            if (e is CancellationException) throw e
            log { FrameLogFormat.ev("connectGatt 오류 ${e.javaClass.simpleName}") }
            return null to LinkEnd.OpFailed(BluetoothGatt.GATT_FAILURE)
        }
        val l = Link(address, nameFor(address), transport, config)
        link = l
        try {
            val end = coroutineScope {
                val events = launch { transport.events.collect { onEvent(l, it) } }
                val work = launch {
                    val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { setUp(l) }   // connectGatt부터 알림 구독까지
                    if (ok == null) l.end.complete(LinkEnd.Timeout)
                    if (ok == true) {
                        onReady(l)
                        readInitialState(l)
                    }
                }
                val e = l.end.await()
                events.cancel()
                work.cancel()
                e
            }
            return l to end
        } finally {
            release(l)
        }
    }

    /** GATT-1: 연결 → 서비스 탐색 → FFF1·FFF2 확인 → MTU 247(실패해도 진행) → FFF1 알림 켜기. G-7 서비스는 보지 않는다. */
    private suspend fun setUp(l: Link): Boolean {
        l.connected.await()
        if (l.gatt { discoverServices() } != true) return l.fail(LinkEnd.OpFailed(BluetoothGatt.GATT_FAILURE))
        val t = l.transport
        if (!t.hasCharacteristic(SCALE_SERVICE_UUID, GattSpec.UPLINK) || !t.hasCharacteristic(SCALE_SERVICE_UUID, GattSpec.DOWNLINK)) {
            return l.fail(LinkEnd.ServiceNotFound)
        }
        l.mtu = l.gatt { requestMtu(GattSpec.REQUEST_MTU) } ?: GattSpec.DEFAULT_MTU
        if (l.gatt { enableNotifications(SCALE_SERVICE_UUID, GattSpec.UPLINK) } != true) {
            return l.fail(LinkEnd.OpFailed(BluetoothGatt.GATT_FAILURE))
        }
        return true
    }

    private fun Link.fail(reason: LinkEnd): Boolean {
        end.complete(reason)
        return false
    }

    private fun onReady(l: Link) {
        val device = ScaleDevice(l.address, l.name, modelNumber = null, firmwareRevision = null, mtu = l.mtu)
        l.ready = true
        l.device = device
        store.save(l.address, l.name)
        _lastDeviceAddress.value = l.address
        _scanResults.value = emptyList()
        log { FrameLogFormat.ev("연결됨 ${l.address} mtu=${l.mtu}") }
        _connection.value = ConnectionState.Connected(device)
    }

    /** GATT-1 뒤 단계: 0x06 → 0x05 → 0x02 Read → DIS. 읽기 실패는 연결을 끊지 않는다. */
    private suspend fun readInitialState(l: Link) {
        send(ScaleCommand.ReadUnit)   // 실패·시간 초과면 send()가 onUnitReadFailed()
        send(ScaleCommand.ReadBattery)
        send(ScaleCommand.ReadTimer)  // status.scaleTimer만 채운다. timerEvents로 내지 않는다
        val model = l.gatt { read(GattSpec.DIS_SERVICE, GattSpec.MODEL_NUMBER) }?.decodeToString()
        val firmware = l.gatt { read(GattSpec.DIS_SERVICE, GattSpec.FIRMWARE_REVISION) }?.decodeToString()
        val device = l.device ?: return
        if (!l.end.isCompleted) {
            _connection.value = ConnectionState.Connected(device.copy(modelNumber = model, firmwareRevision = firmware, disReadDone = true))
        }
    }

    /** GATT 작업 큐: 한 번에 하나, 5 s 제한. 시간 초과면 스택이 아직 바쁘므로 다음 작업을 보내지 않고 연결을 끊김으로 처리한다. */
    private suspend fun <T> Link.gatt(block: suspend GattTransport.() -> T): T? = gattMutex.withLock {
        if (end.isCompleted) return null
        val box = try {
            withTimeoutOrNull(GATT_OP_TIMEOUT_MS) { Box(transport.block()) }
        } catch (e: SecurityException) {
            end.complete(LinkEnd.Permission)
            return null
        } catch (e: RuntimeException) {   // API 33 write가 binder 오류를 RuntimeException으로 다시 던진다
            if (e is CancellationException) throw e
            log { FrameLogFormat.ev("GATT 작업 오류 ${e.javaClass.simpleName}") }
            end.complete(LinkEnd.OpFailed(BluetoothGatt.GATT_FAILURE))
            return null
        }
        if (box == null) {
            log { FrameLogFormat.ev("GATT 작업 시간 초과") }
            end.complete(LinkEnd.Timeout)
            return null
        }
        box.value
    }

    /** 한 번만 실제로 해제한다. 대기 응답은 끊김으로 끝낸다. */
    private fun release(l: Link) {
        if (l.released) return
        l.released = true
        l.end.complete(LinkEnd.Released)
        l.transport.close()
        if (link === l) link = null
        pending?.takeIf { it.link === l }?.let {
            pending = null
            it.frame.complete(null)
        }
        l.reassembler.reset()
        reducer.reset()
        _status.value = reducer.status
        log { FrameLogFormat.ev("close ${l.address}") }
    }

    // ---- 수신 ----

    private fun onEvent(l: Link, e: GattEvent) {
        if (link !== l) return   // 버린 transport의 남은 이벤트는 현재 연결에 반영하지 않는다(6절 close)
        when (e) {
            is GattEvent.ConnectionChanged -> {
                log { FrameLogFormat.ev("연결 상태 connected=${e.connected} status=${e.status}") }
                if (e.connected) l.connected.complete(Unit) else l.end.complete(LinkEnd.Lost(e.status))
            }
            is GattEvent.Notification -> if (e.characteristic == GattSpec.UPLINK) onNotification(l, e.value, e.atNanos)
        }
    }

    private fun onNotification(l: Link, value: ByteArray, atNanos: Long) {
        log { FrameLogFormat.rx(value) }
        for (r in l.reassembler.feed(value)) {
            when (r) {
                is DecodeResult.Error -> log { FrameLogFormat.ev("디코딩 오류 ${r.error}") }
                is DecodeResult.Frame -> onFrame(l, r.frame, atNanos)
            }
        }
    }

    /** 한 알림의 프레임은 들어온 순서대로, 순서에 기대지 않고 처리한다. */
    private fun onFrame(l: Link, frame: ScaleFrame, atNanos: Long) {
        if (frame is ScaleFrame.Unknown) {   // 예: Notify 0x04(C-04-1). 상태·대기 응답·연결을 바꾸지 않는다
            log { FrameLogFormat.ev("버림 $frame") }
            return
        }
        val p = pending
        val key = frame.responseKey()
        if (p != null && p.link === l && p.key == key && atNanos >= p.sentAtNanos) {
            pending = null
            p.frame.complete(frame)
        } else if (key != null) {
            // 요청과 짝이 없는 응답: 다른 앱이 같은 저울에 붙어 있거나 시간 초과 뒤 늦게 온 응답. 상태(Read 값)는 저울의 실제 값이라 그대로 반영한다
            log { FrameLogFormat.ev("짝 없는 응답 $frame") }
        }
        reducer.onFrame(frame, atNanos)?.let { _readings.tryEmit(it) }
        _status.value = reducer.status
        if (frame is ScaleFrame.TimerState && frame.op == Op.Notify) {
            ScaleTimerState.entries.getOrNull(frame.stateRaw - 1)?.let { _timerEvents.tryEmit(it) }   // stateRaw 01/02/03 = enum 순서
        }
    }

    // ---- 명령 ----

    override suspend fun send(command: ScaleCommand): CommandResult = withContext(dispatcher) {
        commandMutex.withLock { sendLocked(command) }
    }

    private suspend fun sendLocked(command: ScaleCommand): CommandResult {
        val l = link?.takeIf { it.ready && !it.end.isCompleted } ?: return CommandResult.Failed(CommandFailure.NotConnected)
        val bytes = when (val e = FrameCodec.encode(command)) {
            is EncodeResult.Ok -> e.bytes
            EncodeResult.InvalidName -> return CommandResult.Failed(CommandFailure.InvalidName)
        }
        if (bytes.size > l.mtu - GattSpec.ATT_HEADER) return CommandResult.Failed(CommandFailure.PayloadTooLarge)   // F-11 나뉜 송신 미확정
        val p = PendingResponse(l, command.responseKey())
        pending = p   // 쓰기 전에 등록: 응답이 쓰기 콜백보다 먼저 와도 잡는다
        try {
            log { FrameLogFormat.tx(bytes) }
            // 로컬 쓰기 완료(onCharacteristicWrite)는 큐 진행에만 쓰고 성공으로 보지 않는다(G-5)
            val written = l.gatt {
                p.sentAtNanos = clock.nowNanos()
                writeNoResponse(SCALE_SERVICE_UUID, GattSpec.DOWNLINK, bytes)
            } == true
            val result = when {
                !written -> CommandResult.Failed(if (l.end.isCompleted) CommandFailure.Disconnected else CommandFailure.NotConnected)
                else -> {
                    val frame = withTimeoutOrNull(config.responseTimeoutMs) { p.frame.await() }
                    when {
                        frame != null -> resultOf(l, command, frame)
                        p.frame.isCompleted -> CommandResult.Failed(CommandFailure.Disconnected)
                        else -> CommandResult.Failed(CommandFailure.Timeout)   // 재시도하지 않는다(C-W-2)
                    }
                }
            }
            if (command == ScaleCommand.ReadUnit && result is CommandResult.Failed && !l.end.isCompleted) {
                reducer.onUnitReadFailed()
                _status.value = reducer.status
            }
            return result
        } finally {
            if (pending === p) pending = null   // 취소·시간 초과: 뒤에 온 같은 키 프레임이 어떤 send도 완료하지 않게
        }
    }

    private fun resultOf(l: Link, command: ScaleCommand, frame: ScaleFrame): CommandResult {
        if (frame !is ScaleFrame.WriteAck) return CommandResult.Response(frame)
        if (!frame.success) return CommandResult.Failed(CommandFailure.Rejected)
        // Write 응답 성공이 상태를 확정하는 유일한 근거다(C-02-3). Notify를 기다리지 않는다
        reducer.onCommandSucceeded(command)
        _status.value = reducer.status
        if (command == ScaleCommand.PowerOff) l.powerOffAcked = true
        return CommandResult.Success
    }

    private inline fun log(line: () -> String) {
        if (debugFrameLog) logSink(line())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val GATT_OP_TIMEOUT_MS = 5_000L
        const val SCAN_DURATION_MS = 10_000L
        const val SCAN_RESTART_GAP_NANOS = 6_000_000_000L
        const val NANOS_PER_MS = 1_000_000L
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val GATT_ERROR_133 = 133
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)   // 7회째부터 30 s
    }
}
