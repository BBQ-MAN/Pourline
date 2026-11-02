package com.timemoredot.app.fake

import com.timemoredot.app.data.SessionDraft
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.brew.BrewSample
import com.timemoredot.brew.SampleBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 가짜도 실제 saveDraft 규칙(in_progress 행만 갱신)을 따른다(architecture.md 8.1·8.3). */
class FakeBrewRepositoryTest {
    private fun blob(n: Int) = SampleBuffer().also { b -> repeat(n) { b.append(BrewSample(it * 100, 1f, 0f, 0)) } }.toBlob()
    private fun draft(id: Long?, n: Int = 2) = SessionDraft(id, 1000, null, 2.0, 200, blob(n))

    @Test fun `finish 뒤 saveDraft는 status·dose·note를 바꾸지 않는다`() = runTest {
        val repo = FakeBrewRepository()
        val id = repo.saveDraft(draft(null))
        repo.finish(id, 15.0, "memo", SessionStatus.Completed)
        assertEquals(id, repo.saveDraft(draft(id, n = 5)))
        val s = repo.summary(id)!!
        assertEquals(SessionStatus.Completed, s.status)
        assertEquals(15.0, s.doseGrams!!, 0.0)
        assertEquals("memo", s.note)
        assertEquals(2, repo.samples(id)!!.size)
    }

    @Test fun `deleteSession 뒤 saveDraft는 행을 다시 만들지 않는다`() = runTest {
        val repo = FakeBrewRepository()
        val id = repo.saveDraft(draft(null))
        repo.deleteSession(id)
        assertEquals(id, repo.saveDraft(draft(id)))
        assertNull(repo.summary(id))
    }
}
