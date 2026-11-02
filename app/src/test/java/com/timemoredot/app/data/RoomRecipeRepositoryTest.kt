package com.timemoredot.app.data

import androidx.room.Room
import app.cash.turbine.test
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.RecipeStep
import com.timemoredot.brew.ValveEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomRecipeRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomRecipeRepository

    @Before fun setUp() {
        db = buildAppDatabase(Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).allowMainThreadQueries())
        repo = RoomRecipeRepository(db.recipeDao())
    }

    @After fun tearDown() = db.close()

    private val steps = listOf(RecipeStep("뜸", 50.0, 30), RecipeStep("끝", 250.0, 90))
    private fun recipe(name: String, finish: Int? = null) = Recipe(0, name, 15.0, 250.0, steps, finish)

    @Test fun roundTripAndCrud() = runTest {
        db.recipeDao().delete(1); (1L..5L).forEach { db.recipeDao().delete(it) }   // 시드 제거
        val id = repo.upsert(recipe("b", 120))
        val idNull = repo.upsert(recipe("a"))
        assertEquals(recipe("b", 120).copy(id = id), repo.get(id))
        assertEquals(120, repo.get(id)!!.finishAtSec)
        assertNull(repo.get(idNull)!!.finishAtSec)
        assertEquals(listOf("a", "b"), repo.observeAll().first().map { it.name })
        repo.upsert(repo.get(id)!!.copy(name = "c"))
        assertEquals(listOf("a", "c"), repo.observeAll().first().map { it.name })
        repo.delete(id)
        assertNull(repo.get(id))
        repo.observeAll().test { assertEquals(listOf("a"), awaitItem().map { it.name }); cancelAndIgnoreRemainingEvents() }
    }

    @Test fun deletingRecipeKeepsSessions() = runTest {
        val id = repo.upsert(recipe("x"))
        val r = repo.get(id)!!
        val brews = RoomBrewRepository(db, db.brewDao())
        val sid = brews.saveDraft(SessionDraft(null, 1, r, 1.0, 1, byteArrayOf(1)))
        brews.finish(sid, 15.0, "", SessionStatus.Completed)
        repo.delete(id)
        val s = brews.summary(sid)!!
        assertEquals("x", s.recipeName)
        assertEquals(r, s.recipe)
    }

    @Test fun stepValvesRoundTrip() = runTest {
        val devil = DefaultRecipes.all.single { it.name == "테츠 카스야 악마의 레시피" }
        val noValve = devil.copy(name = "밸브 없음", steps = devil.steps.map { it.copy(valveOpen = null) })
        val id = repo.upsert(devil)
        val idNo = repo.upsert(noValve)
        assertEquals(devil.copy(id = id), repo.get(id))
        assertEquals(noValve.copy(id = idNo), repo.get(idNo))
        assertEquals(listOf(ValveEvent(0, true), ValveEvent(75, false), ValveEvent(105, true)), repo.get(id)!!.valveSchedule)
        assertEquals(emptyList<ValveEvent>(), repo.get(idNo)!!.valveSchedule)
    }
}
