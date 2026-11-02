package com.timemoredot.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** BluetoothGatt를 감싼 얇은 경계(architecture.md 6절). 테스트는 FakeGattTransport를 쓴다. suspend 함수는 콜백까지 기다린다.
 *  호출자(BleScaleClient)가 작업을 한 번에 하나만 부르고 5 s 시간 제한을 건다. 권한이 없으면 SecurityException을 그대로 던진다(호출자가 PermissionMissing으로 바꾼다). */
interface GattTransport {
    /** 연결 상태·알림 바이트(복사본). transport 생성 시점(connectGatt 직후)부터 Channel(UNLIMITED)에 쌓고 receiveAsFlow()로 낸다.
     *  수집자는 하나뿐(BleScaleClient). 수집 전에 온 ConnectionChanged(빠른 133)도 잃지 않는다. */
    val events: Flow<GattEvent>
    suspend fun discoverServices(): Boolean
    suspend fun requestMtu(mtu: Int): Int?               // 실패면 null
    suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean  // setCharacteristicNotification + CCCD
    suspend fun writeNoResponse(service: UUID, characteristic: UUID, value: ByteArray): Boolean
    suspend fun read(service: UUID, characteristic: UUID): ByteArray?
    fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean
    fun close()                                          // 멱등. 두 번째부터는 아무 일도 하지 않는다. 뒤의 suspend 작업은 즉시 실패(false/null).
                                                         // events 채널을 닫아 Flow를 끝낸다(이미 쌓인 이벤트는 수집자가 받은 뒤 끝남). 수집 코루틴이 연결마다 남지 않는다.
                                                         // BleScaleClient는 버린 transport의 남은 이벤트를 현재 연결에 반영하지 않는다(수집 코루틴이 자기 transport가 현재 것일 때만 처리)
}

sealed interface GattEvent {
    data class ConnectionChanged(val connected: Boolean, val status: Int) : GattEvent
    data class Notification(val characteristic: UUID, val value: ByteArray, val atNanos: Long) : GattEvent
}

fun interface GattTransportFactory { fun connect(address: String): GattTransport }   // connectGatt(autoConnect=false, TRANSPORT_LE)

/** 대기 중인 GATT 작업 종류. 콜백은 종류가 같을 때만 그 작업을 완료한다(architecture.md 6절 작업 큐). */
internal enum class GattOpKind { DiscoverServices, Mtu, DescriptorWrite, CharacteristicWrite, CharacteristicRead }

/** 진행 중인 GATT 작업 한 칸. 콜백 스레드(바인더)와 코루틴 사이에서 쓴다.
 *  대기 작업이 없거나 종류가 다른 콜백, 취소·시간 초과로 버려진 작업의 늦은 콜백은 아무 작업도 완료하지 않는다. */
internal class GattOpSlot(private val log: (String) -> Unit = {}) {
    private class Pending(val kind: GattOpKind, val result: CompletableDeferred<Any?>)
    private val lock = Any()
    private var pending: Pending? = null
    private var closed = false

    /** start: 시스템 호출(false면 즉시 실패). 반환: 콜백 값, 실패·닫힘이면 null. 동시에 두 작업이면 뒤 작업은 즉시 실패. */
    suspend fun run(kind: GattOpKind, start: () -> Boolean): Any? {
        val p = Pending(kind, CompletableDeferred())
        synchronized(lock) {
            if (closed || pending != null) return null
            pending = p
        }
        try {
            if (!start()) return null
            return p.result.await()
        } finally {
            synchronized(lock) { if (pending === p) pending = null }
        }
    }

    /** 콜백. value null = 실패 status. 대기 작업을 완료했으면 true. */
    fun complete(kind: GattOpKind, value: Any?): Boolean {
        val p = synchronized(lock) {
            pending?.takeIf { it.kind == kind }?.also { pending = null }
        }
        if (p == null) {
            log(FrameLogFormat.ev("GATT 콜백 버림 $kind"))
            return false
        }
        p.result.complete(value)
        return true
    }

    /** 대기 작업을 실패로 끝내고 뒤 작업을 모두 즉시 실패시킨다. */
    fun close() {
        val p = synchronized(lock) {
            closed = true
            pending.also { pending = null }
        }
        p?.result?.complete(null)
    }
}
