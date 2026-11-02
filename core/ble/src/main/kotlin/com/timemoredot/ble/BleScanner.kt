package com.timemoredot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.timemoredot.protocol.Advertisement
import com.timemoredot.protocol.MANUFACTURER_FILTER_DATA
import com.timemoredot.protocol.MANUFACTURER_FILTER_MASK
import com.timemoredot.protocol.SCALE_MANUFACTURER_ID
import com.timemoredot.protocol.SCALE_SERVICE_UUID

/** 스캔 결과 하나. 실제는 ScanResult에서 만든다. */
internal data class ScanHit(val address: String, val rssi: Int, val advertisement: Advertisement)

/** 스캐너 경계. 테스트는 가짜를 쓴다. 권한이 없으면 SecurityException을 던진다(BleScaleClient가 PermissionMissing으로 바꾼다).
 *  콜백은 메인 스레드에서 온다(ScanCallback 기본). */
internal interface BleScanner {
    fun start(onResult: (ScanHit) -> Unit, onFailed: (Int) -> Unit)
    fun stop()
}

/** architecture.md 6절 스캔: ScanFilter 두 개(OR). 이름으로 거르지 않는다(A-2, A-3). */
internal fun scaleScanFilters(): List<ScanFilter> = listOf(
    ScanFilter.Builder().setServiceUuid(ParcelUuid(SCALE_SERVICE_UUID)).build(),
    // 기기 유형(값[1])만 맞추고 방송 유형(값[0], 01 화이트리스트 / 02 일반)은 가리지 않는다
    ScanFilter.Builder()
        .setManufacturerData(SCALE_MANUFACTURER_ID, MANUFACTURER_FILTER_DATA.copyOf(), MANUFACTURER_FILTER_MASK.copyOf())
        .build(),
)

@SuppressLint("MissingPermission")
internal class AndroidBleScanner(private val context: Context) : BleScanner {
    private var callback: ScanCallback? = null

    private fun leScanner() = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner

    override fun start(onResult: (ScanHit) -> Unit, onFailed: (Int) -> Unit) {
        val scanner = leScanner() ?: return onFailed(-1)   // 블루투스 꺼짐
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result.toHit())
            override fun onScanFailed(errorCode: Int) = onFailed(errorCode)
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(scaleScanFilters(), settings, cb)
        callback = cb
    }

    override fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            leScanner()?.stopScan(cb)
        } catch (e: IllegalStateException) {
            // 블루투스가 이미 꺼짐: 시스템이 스캔을 이미 멈췄다
        }
    }
}

private fun ScanResult.toHit(): ScanHit {
    val record = scanRecord
    return ScanHit(
        address = device.address,
        rssi = rssi,
        advertisement = Advertisement(
            name = record?.deviceName,   // 광고 이름만. BluetoothDevice.getName()(캐시 이름)은 쓰지 않는다
            serviceUuids = record?.serviceUuids?.map { it.uuid }.orEmpty(),
            manufacturerData = record?.getManufacturerSpecificData(SCALE_MANUFACTURER_ID)
                ?.let { mapOf(SCALE_MANUFACTURER_ID to it) }.orEmpty(),
        ),
    )
}
