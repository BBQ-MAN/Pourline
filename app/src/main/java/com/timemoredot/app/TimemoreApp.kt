package com.timemoredot.app

import android.app.Application

/** container는 첫 접근 때 만든다. MainActivity.onCreate가 VariantHooks.onActivityCreate를 먼저 불러 모드를 정한다(architecture.md 8.1). */
class TimemoreApp : Application() {
    val container: AppContainer by lazy { VariantHooks.overrideContainer(this) ?: AppContainer(this) }
}
