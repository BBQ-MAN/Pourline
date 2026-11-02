package com.timemoredot.app

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.room.Room
import com.timemoredot.app.data.AppDatabase
import com.timemoredot.app.data.buildAppDatabase
import com.timemoredot.ble.demo.DemoScale

/** debug: 시작 컴포넌트가 DemoLauncher면 이 프로세스를 데모 모드로 만든다(architecture.md 10.1.4). 모드는 프로세스당 하나이고 저장하지 않는다. */
internal object VariantHooks {
    private const val DEMO_ALIAS = "com.timemoredot.app.DemoLauncher"
    private var demoMode = false
    private var containerMade = false

    fun onActivityCreate(activity: Activity) {
        val wantsDemo = activity.intent?.component?.className == DEMO_ALIAS
        if (!containerMade) demoMode = wantsDemo
        else if (wantsDemo != demoMode) Toast.makeText(activity, "다른 모드로 실행 중입니다. 앱을 완전히 닫고 다시 여세요", Toast.LENGTH_LONG).show()
    }

    fun overrideContainer(app: Application): AppContainer? {
        containerMade = true
        if (!demoMode) return null
        return AppContainer(
            app,
            scaleFactory = { a, s, c -> DemoScale.client(a, s, c) },
            databaseFactory = { buildAppDatabase(Room.databaseBuilder(it, AppDatabase::class.java, "timemore_demo.db")) },
        )
    }

    /** 테스트 전용: 프로세스 상태를 처음으로 되돌린다. */
    fun resetForTest() { demoMode = false; containerMade = false }
}
