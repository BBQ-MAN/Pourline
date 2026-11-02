package com.timemoredot.protocol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/** architecture.md 5.5. 호출은 기록만 하고 상태 전이는 테스트가 set*로 정한다. */
class FakeScaleClient(initial: ConnectionState = ConnectionState.Idle) : ScaleClient {
    private val _connection = MutableStateFlow(initial)
    private val _scanResults = MutableStateFlow<List<ScanCandidate>>(emptyList())
    private val _status = MutableStateFlow(ScaleStatus.Unknown)
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64)
    private val _timerEvents = MutableSharedFlow<ScaleTimerState>(extraBufferCapacity = 16)
    private val _lastDevice = MutableStateFlow<String?>(null)
    private val _sent = CopyOnWriteArrayList<ScaleCommand>()
    private val _calls = CopyOnWriteArrayList<String>()
    private val responses = ConcurrentHashMap<KClass<out ScaleCommand>, CommandResult>()

    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    override val scanResults: StateFlow<List<ScanCandidate>> = _scanResults.asStateFlow()
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()
    override val timerEvents: SharedFlow<ScaleTimerState> = _timerEvents.asSharedFlow()
    override val lastDeviceAddress: StateFlow<String?> = _lastDevice.asStateFlow()

    /** 받은 명령 기록(복사본). */
    val sent: List<ScaleCommand> get() = _sent.toList()
    /** "startScan", "connect:<addr>", "refreshEnvironment" 등 호출 기록(복사본). */
    val calls: List<String> get() = _calls.toList()

    fun setConnection(state: ConnectionState) { _connection.value = state }
    fun setStatus(status: ScaleStatus) { _status.value = status }
    fun setScanResults(list: List<ScanCandidate>) { _scanResults.value = list }
    fun setLastDevice(address: String?) { _lastDevice.value = address }
    suspend fun emit(reading: ScaleReading) = _readings.emit(reading)

    /** 실제 Notify처럼 status.scaleTimer를 바꾸고 timerEvents로 낸다. ReadTimer 응답 흉내는 setStatus를 쓴다. */
    suspend fun emitTimerNotify(state: ScaleTimerState) {
        _status.update { it.copy(scaleTimer = state) }
        _timerEvents.emit(state)
    }

    /** 기본 응답은 Success. */
    fun respondWith(command: KClass<out ScaleCommand>, result: CommandResult) { responses[command] = result }

    override fun refreshEnvironment() { _calls += "refreshEnvironment" }
    override fun startScan() { _calls += "startScan" }
    override fun stopScan() { _calls += "stopScan" }
    override fun connect(address: String) { _calls += "connect:$address" }
    override fun connectLastDevice(): Boolean {
        _calls += "connectLastDevice"
        return _lastDevice.value != null
    }
    override fun disconnect() { _calls += "disconnect" }

    /** Success면 실제 클라이언트와 같은 규칙(afterSuccess)으로 status를 바꾼 뒤 반환한다. timerEvents로는 내지 않는다. */
    override suspend fun send(command: ScaleCommand): CommandResult {
        _sent += command
        val result = responses[command::class] ?: CommandResult.Success
        if (result == CommandResult.Success) _status.update { it.afterSuccess(command) }
        return result
    }
}

/** 합성 시계열 도우미. 10 Hz, 단위 g. */
fun readings(
    startNanos: Long,
    vararg weightsGrams: Double,
    flowGramsPerSec: Double = 0.0,
    intervalMs: Long = 100,
): List<ScaleReading> = weightsGrams.mapIndexed { i, w ->
    ScaleReading(
        weightGrams = w,
        scaleFlowGramsPerSec = flowGramsPerSec,
        scaleTimerSec = 0,
        overload = false,
        sourceUnit = WeightUnit.Gram,
        atNanos = startNanos + i * intervalMs * 1_000_000,
    )
}
