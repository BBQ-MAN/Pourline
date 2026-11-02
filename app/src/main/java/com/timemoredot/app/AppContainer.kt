package com.timemoredot.app

import android.app.Application
import android.os.SystemClock
import android.net.Uri
import android.util.Log
import com.timemoredot.app.data.AppDatabase
import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.data.RecipeRepository
import com.timemoredot.app.data.RoomBrewRepository
import com.timemoredot.app.data.RoomRecipeRepository
import com.timemoredot.app.data.createAppDatabase
import com.timemoredot.ble.BleGattTransport
import com.timemoredot.ble.BleScaleClient
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.IOException

/** architecture.md 8.1. */
class AppContainer(
    private val app: Application,
    scaleFactory: (Application, CoroutineScope, MonotonicClock) -> ScaleClient = ::bleScale,
    databaseFactory: (Application) -> AppDatabase = ::createAppDatabase,
) {
    // 잡히지 않은 예외로 프로세스가 죽지 않게 하는 마지막 방어선. 로그만 남기고 다시 던지지 않는다(3절)
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e("TimemoreApp", "appScope", e) },
    )
    val clock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtimeNanos() }
    val scale: ScaleClient = scaleFactory(app, appScope, clock)
    val prefs: AppPrefs = SharedPrefsAppPrefs(app)
    val permission: PermissionMonitor = AndroidPermissionMonitor(app, scale)
    val database: AppDatabase = databaseFactory(app)
    val recipes: RecipeRepository = RoomRecipeRepository(database.recipeDao())
    val brews: BrewRepository = RoomBrewRepository(database, database.brewDao())
    val brewController: BrewSessionController = BrewSessionController(
        scale, brews, prefs, clock, System::currentTimeMillis, appScope,
        logError = { msg, e -> Log.w("TimemoreApp", msg, e) },
    )

    /** 파일 선택기가 돌려준 Uri 하나를 내보내기 대상으로 바꾼다(8.4). IO에서 열고 쓰고 닫는다. 열기 실패(스트림 null)도 예외다. */
    fun csvTarget(uri: Uri): CsvTarget = CsvTarget { block ->
        withContext(Dispatchers.IO) {
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("openOutputStream null")
            out.bufferedWriter(Charsets.UTF_8).use { block(it) }
        }
    }

    init {
        scale.connectLastDevice()
    }
}

private fun bleScale(app: Application, scope: CoroutineScope, clock: MonotonicClock): ScaleClient =
    BleScaleClient(app, scope, BleGattTransport.factory(app, BuildConfig.DEBUG), clock, debugFrameLog = BuildConfig.DEBUG)
