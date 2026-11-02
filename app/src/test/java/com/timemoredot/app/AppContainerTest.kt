package com.timemoredot.app

import com.timemoredot.protocol.FakeScaleClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {
    @Test fun `시작할 때 connectLastDevice를 한 번 부른다`() {
        val scale = FakeScaleClient()
        val container = AppContainer(RuntimeEnvironment.getApplication(), scaleFactory = { _, _, _ -> scale })
        assertSame(scale, container.scale)
        assertEquals(1, scale.calls.count { it == "connectLastDevice" })
        assertEquals(1, scale.calls.size)
    }

    @Test fun `appScope에 CoroutineExceptionHandler가 있고 컨트롤러·저장소가 연결된다`() {
        val container = AppContainer(RuntimeEnvironment.getApplication(), scaleFactory = { _, _, _ -> FakeScaleClient() })
        org.junit.Assert.assertNotNull(container.appScope.coroutineContext[kotlinx.coroutines.CoroutineExceptionHandler])
        org.junit.Assert.assertNotNull(container.brewController)
        org.junit.Assert.assertNotNull(container.recipes)
        org.junit.Assert.assertNotNull(container.brews)
        container.database.close()
    }
}
