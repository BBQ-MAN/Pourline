package com.timemoredot.app.data

import androidx.room.Room
import app.cash.turbine.test
import com.timemoredot.brew.BrewSample
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.RecipeStep
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.ValveEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomBrewRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomBrewRepository

    @Before fun setUp() {
        db = buildAppDatabase(Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).allowMainThreadQueries())
        repo = RoomBrewRepository(db, db.brewDao())
    }

    @After fun tearDown() = db.close()

    private fun blob(vararg w: Float) = SampleBuffer().also { b ->
        w.forEachIndexed { i, x -> b.append(BrewSample(i * 100, x, 1f, 0)) }
    }.toBlob()

    private val recipe = Recipe(7, "R", 15.0, 250.0, listOf(RecipeStep("a", 250.0, 60)))
    private fun draft(id: Long? = null, at: Long = 1000, b: ByteArray = blob(1f, 2f)) =
        SessionDraft(id, at, recipe, 2.0, 200, b)

    @Test fun saveDraftTwiceKeepsOneRow() = runTest {
        val id = repo.saveDraft(draft())
        assertEquals(id, repo.saveDraft(draft(id, b = blob(1f, 2f, 3f))))
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM brew_sessions").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(3, repo.samples(id)!!.size)
        assertEquals("R", repo.summary(id)!!.recipeName)
    }

    @Test fun finishStoresValuesAndHidesInProgress() = runTest {
        val b = blob(1f, 2f)
        val id = repo.saveDraft(draft(b = b))
        repo.observeSessions().test {
            assertEquals(emptyList<SessionSummary>(), awaitItem())
            repo.finish(id, 15.0, "memo", SessionStatus.Completed)
            val s = awaitItem().single()
            assertEquals(id, s.id)
            assertEquals(15.0, s.doseGrams!!, 0.0)
            assertEquals("memo", s.note)
            assertEquals(SessionStatus.Completed, s.status)
            assertEquals(recipe, s.recipe)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(s(id), repo.summary(id))
        assertArrayEquals(b, repo.samples(id)!!.toBlob())
    }

    private suspend fun s(id: Long) = repo.summary(id)

    @Test fun findDraftExcludesAndPicksLatest() = runTest {
        val a = repo.saveDraft(draft(at = 1000))
        val b = repo.saveDraft(draft(at = 2000))
        val c = repo.saveDraft(draft(at = 3000))
        assertEquals(c, repo.findDraft(null)!!.id)
        assertEquals(b, repo.findDraft(c)!!.id)
        repo.finish(b, null, "", SessionStatus.Interrupted)
        assertEquals(a, repo.findDraft(c)!!.id)
        repo.deleteSession(a)
        assertNull(repo.findDraft(c))
    }

    @Test fun deleteCascadesSamples() = runTest {
        val id = repo.saveDraft(draft())
        assertNotNull(repo.samples(id))
        repo.deleteSession(id)
        assertNull(repo.samples(id))
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM brew_samples").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun corruptBlobGivesNull() = runTest {
        val id = repo.saveDraft(draft(b = byteArrayOf(9, 9, 9)))
        assertNull(repo.samples(id))
        assertNull(repo.samples(12345))
    }

    @Test fun updateNote() = runTest {
        val id = repo.saveDraft(draft())
        repo.updateNote(id, "n")
        assertEquals("n", repo.summary(id)!!.note)
    }

    @Test fun sessionKeepsRecipeStepValves() = runTest {
        val devil = DefaultRecipes.all.single { it.name == "테츠 카스야 악마의 레시피" }
        val id = repo.saveDraft(SessionDraft(null, 1000, devil, 280.0, 180_000, blob(1f)))
        assertEquals(devil, repo.summary(id)!!.recipe)
        assertEquals(listOf(ValveEvent(0, true), ValveEvent(75, false), ValveEvent(105, true)), repo.summary(id)!!.recipe!!.valveSchedule)
    }

    @Test fun saveDraftAfterFinishDoesNotChangeRow() = runTest {
        val id = repo.saveDraft(draft())
        repo.finish(id, 15.0, "memo", SessionStatus.Completed)
        assertEquals(id, repo.saveDraft(draft(id, b = blob(9f))))
        val s = repo.summary(id)!!
        assertEquals(SessionStatus.Completed, s.status)
        assertEquals(15.0, s.doseGrams!!, 0.0)
        assertEquals("memo", s.note)
        assertEquals(2, repo.samples(id)!!.size)
    }

    @Test fun saveDraftAfterDeleteDoesNotRecreateRow() = runTest {
        val id = repo.saveDraft(draft())
        repo.deleteSession(id)
        assertEquals(id, repo.saveDraft(draft(id)))
        assertNull(repo.summary(id))
        assertNull(repo.samples(id))
    }
}
