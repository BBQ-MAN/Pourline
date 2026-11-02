package com.timemoredot.app.fake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** 테스트 끝에 clear()로 viewModelScope를 닫는다(resetMain 뒤 Main으로 보내지는 일이 없게). */
class VmStore {
    val store = ViewModelStore()
    var n = 0
    /** 부를 때마다 새 키라 새 인스턴스다. */
    inline fun <reified T : ViewModel> create(noinline make: () -> T): T =
        ViewModelProvider.create(store, viewModelFactory { initializer { make() } })["vm${n++}", T::class]
    fun clear() = store.clear()
}
