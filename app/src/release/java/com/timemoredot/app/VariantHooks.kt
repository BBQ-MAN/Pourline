package com.timemoredot.app

import android.app.Activity
import android.app.Application

/** release: 변형 훅은 아무것도 바꾸지 않는다(architecture.md 10.1.4). debug 파일과 시그니처가 같다. */
internal object VariantHooks {
    @Suppress("UNUSED_PARAMETER")
    fun onActivityCreate(activity: Activity) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun overrideContainer(app: Application): AppContainer? = null
}
