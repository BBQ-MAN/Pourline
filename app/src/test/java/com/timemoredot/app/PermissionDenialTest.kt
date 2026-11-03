package com.timemoredot.app

import com.timemoredot.app.PermissionDenial.deniedOnceAfterResult
import com.timemoredot.app.PermissionDenial.deniedOnceOnResume
import com.timemoredot.app.PermissionDenial.isPermanent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 8.1 영구 거부 판정. F-B2-1(프로브 B: 시스템은 '다시 묻지 않음'인데 앱 기록이 없어 막힌 화면)과 B2 관찰(설정에서 허용 뒤 deniedOnce 남음). */
class PermissionDenialTest {
    @Test fun instantDenialWithoutRecordIsPermanent() {
        // F-B2-1: deniedOnce 기록 없음, 대화상자 없이 40 ms 만에 거부 → 영구 거부(설정 열기)
        assertTrue(isPermanent(granted = false, rationale = false, deniedOnce = false, elapsedMs = 40))
    }

    @Test fun dialogDismissedOutsideIsNotPermanent() {
        // 처음 요청에서 대화상자를 바깥 눌러 닫음: rationale false지만 사람이 닫느라 시간이 걸렸다 → Denied(다시 요청 가능)
        assertFalse(isPermanent(granted = false, rationale = false, deniedOnce = false, elapsedMs = 1_800))
        assertEquals(false, deniedOnceAfterResult(granted = false, rationale = false, deniedOnce = false))
    }

    @Test fun secondRealDenialIsPermanentRegardlessOfTime() {
        val afterFirst = deniedOnceAfterResult(granted = false, rationale = true, deniedOnce = false)
        assertTrue(afterFirst)
        assertFalse(isPermanent(granted = false, rationale = true, deniedOnce = afterFirst, elapsedMs = 2_000))
        assertTrue(isPermanent(granted = false, rationale = false, deniedOnce = afterFirst, elapsedMs = 2_000))
    }

    @Test fun grantedIsNeverPermanentAndClearsRecord() {
        assertFalse(isPermanent(granted = true, rationale = false, deniedOnce = true, elapsedMs = 10))
        assertFalse(deniedOnceAfterResult(granted = true, rationale = false, deniedOnce = true))
    }

    @Test fun resumeWithPermissionGrantedInSettingsClearsRecord() {
        assertFalse(deniedOnceOnResume(allGranted = true, deniedOnce = true))
        assertTrue(deniedOnceOnResume(allGranted = false, deniedOnce = true))
    }
}
