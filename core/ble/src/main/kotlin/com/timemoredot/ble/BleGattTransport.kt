package com.timemoredot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** 실제 GATT 연결 하나(android-ble 3~6절). 콜백은 바이트를 복사해 Channel에 넣거나 대기 작업을 완료하기만 한다.
 *  권한 검사는 호출자(BleScaleClient)가 호출 직전에 한다. SecurityException은 호출자에게 던지고, close()에서는 삼킨다. */
@SuppressLint("MissingPermission")
class BleGattTransport internal constructor(
    context: Context,
    address: String,
    private val log: (String) -> Unit,
) : GattTransport {
    private val channel = Channel<GattEvent>(Channel.UNLIMITED)
    override val events: Flow<GattEvent> = channel.receiveAsFlow()
    private val slot = GattOpSlot(log)
    private val closed = AtomicBoolean(false)
    @Volatile private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            channel.trySend(GattEvent.ConnectionChanged(newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS, status))
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            slot.complete(GattOpKind.DiscoverServices, if (status == BluetoothGatt.GATT_SUCCESS) true else null)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            slot.complete(GattOpKind.Mtu, if (status == BluetoothGatt.GATT_SUCCESS) mtu else null)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            slot.complete(GattOpKind.DescriptorWrite, if (status == BluetoothGatt.GATT_SUCCESS) true else null)
        }

        // FFF2는 Write without response지만 Android는 로컬 전송 완료로 이 콜백을 부른다(android-ble 4절). 큐 진행에만 쓴다.
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            slot.complete(GattOpKind.CharacteristicWrite, if (status == BluetoothGatt.GATT_SUCCESS) true else null)
        }

        // API 33 이상: 새 오버로드만 처리하고 super를 부르지 않는다(super가 옛 오버로드를 다시 부른다).
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            slot.complete(GattOpKind.CharacteristicRead, if (status == BluetoothGatt.GATT_SUCCESS) value.copyOf() else null)
        }

        @Deprecated("API 32 이하 경로")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= 33) return
            slot.complete(GattOpKind.CharacteristicRead, if (status == BluetoothGatt.GATT_SUCCESS) c.value?.copyOf() else null)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            notify(c.uuid, value.copyOf())
        }

        @Deprecated("API 32 이하 경로")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            notify(c.uuid, c.value.copyOf())   // 다음 알림이 같은 배열을 덮어쓴다
        }
    }

    init {
        val device = context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        @Suppress("DEPRECATION")   // android-ble 3절 경로(autoConnect=false, TRANSPORT_LE). API 37의 대체 API는 minSdk 26에서 쓸 수 없다
        gatt = device?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) channel.trySend(GattEvent.ConnectionChanged(false, BluetoothGatt.GATT_FAILURE))
    }

    private fun notify(uuid: UUID, value: ByteArray) {
        channel.trySend(GattEvent.Notification(uuid, value, SystemClock.elapsedRealtimeNanos()))
    }

    private fun characteristic(service: UUID, characteristic: UUID): BluetoothGattCharacteristic? =
        gatt?.getService(service)?.getCharacteristic(characteristic)

    override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = characteristic(service, characteristic) != null

    override suspend fun discoverServices(): Boolean =
        slot.run(GattOpKind.DiscoverServices) { gatt?.discoverServices() == true } == true

    override suspend fun requestMtu(mtu: Int): Int? =
        slot.run(GattOpKind.Mtu) { gatt?.requestMtu(mtu) == true } as Int?

    override suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean {
        val g = gatt ?: return false
        val c = characteristic(service, characteristic) ?: return false
        val d = c.getDescriptor(GattSpec.CCCD) ?: return false
        if (!g.setCharacteristicNotification(c, true)) return false
        return slot.run(GattOpKind.DescriptorWrite) { write(g, d, GattSpec.CCCD_ENABLE_NOTIFY) } == true
    }

    override suspend fun writeNoResponse(service: UUID, characteristic: UUID, value: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = characteristic(service, characteristic) ?: return false
        return slot.run(GattOpKind.CharacteristicWrite) { write(g, c, value) } == true
    }

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray? {
        val g = gatt ?: return null
        val c = characteristic(service, characteristic) ?: return null
        return slot.run(GattOpKind.CharacteristicRead) { g.readCharacteristic(c) } as ByteArray?
    }

    /** API 33 전후 쓰기 분기는 이 함수 하나에만 둔다(android-ble 5절). target은 특성(Write without response) 또는 CCCD. */
    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, target: Any, value: ByteArray): Boolean = when (target) {
        is BluetoothGattCharacteristic -> if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(target, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            target.value = value
            g.writeCharacteristic(target)
        }
        is BluetoothGattDescriptor -> if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(target, value) == BluetoothStatusCodes.SUCCESS
        } else {
            target.value = value
            g.writeDescriptor(target)
        }
        else -> false
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        slot.close()
        channel.close()
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            log(FrameLogFormat.ev("close SecurityException"))
        }
        gatt = null
    }

    companion object {
        /** AppContainer가 BleScaleClient에 넘긴다. debugFrameLog는 :app BuildConfig.DEBUG. */
        fun factory(context: Context, debugFrameLog: Boolean): GattTransportFactory {
            val app = context.applicationContext
            return GattTransportFactory { address -> BleGattTransport(app, address, androidLog(debugFrameLog)) }
        }
    }
}

internal fun androidLog(enabled: Boolean): (String) -> Unit = if (enabled) { line -> Log.d(LOG_TAG, line) } else { _ -> }
