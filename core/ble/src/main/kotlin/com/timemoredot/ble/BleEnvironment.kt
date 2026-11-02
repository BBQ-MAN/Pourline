package com.timemoredot.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** 블루투스·권한·위치 상태. 권한은 캐시하지 않고 호출할 때마다 확인한다(android-permissions-security 8절). */
internal interface BleEnvironment {
    fun hasPermissions(): Boolean
    fun isBluetoothOn(): Boolean
    /** 스캔에 위치 서비스가 필요한데 꺼져 있음(API 30 이하 등, BlePermissions.needsLocationService). */
    fun isLocationOffForScan(): Boolean
    /** 앱 범위에서 한 번 호출. on = 블루투스 켜짐/꺼짐. */
    fun observeBluetooth(onChange: (on: Boolean) -> Unit)
}

/** 마지막 연결 기기(architecture.md 6절). */
internal interface DeviceStore {
    val lastAddress: String?
    val lastName: String?
    fun save(address: String, name: String?)
}

internal class AndroidBleEnvironment(private val context: Context) : BleEnvironment {
    override fun hasPermissions(): Boolean = BlePermissions.required(Build.VERSION.SDK_INT)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    override fun isBluetoothOn(): Boolean = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    override fun isLocationOffForScan(): Boolean = BlePermissions.needsLocationService(Build.VERSION.SDK_INT) &&
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) != true

    override fun observeBluetooth(onChange: (on: Boolean) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {   // 값만 넘긴다(anr-audit-and-fix)
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> onChange(true)
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> onChange(false)
                }
            }
        }
        // 시스템 보호 브로드캐스트라 NOT_EXPORTED로도 받는다(android-intent-security)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}

internal class SharedPrefsDeviceStore(context: Context) : DeviceStore {
    private val prefs = context.getSharedPreferences("scale", Context.MODE_PRIVATE)
    override val lastAddress: String? get() = prefs.getString(KEY_ADDRESS, null)
    override val lastName: String? get() = prefs.getString(KEY_NAME, null)

    /** name이 null이면 같은 주소의 이름은 남기고, 다른 주소면 지운다(last_name은 저장 주소의 이름이다). */
    override fun save(address: String, name: String?) {
        val edit = prefs.edit().putString(KEY_ADDRESS, address)
        if (name != null) edit.putString(KEY_NAME, name) else if (address != lastAddress) edit.remove(KEY_NAME)
        edit.apply()
    }

    private companion object {
        const val KEY_ADDRESS = "last_address"
        const val KEY_NAME = "last_name"
    }
}
