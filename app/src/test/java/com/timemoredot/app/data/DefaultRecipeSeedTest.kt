package com.timemoredot.app.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultRecipeSeedTest {
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Test fun newDbHasFiveDefaults() = runTest {
        val db = buildAppDatabase(Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries())
        val got = RoomRecipeRepository(db.recipeDao()).observeAll().first()
        db.close()
        assertEquals(DefaultRecipes.all.sortedBy { it.name }.map { it.copy(id = 0) }, got.map { it.copy(id = 0) })
        assertEquals(5, got.size)
    }

    @Test fun seededOnlyOnceOnFileDb() = runTest {
        val file = File.createTempFile("seed", ".db").also { it.delete() }
        fun open() = buildAppDatabase(Room.databaseBuilder(ctx, AppDatabase::class.java, file.absolutePath).allowMainThreadQueries())
        val db = open()
        val repo = RoomRecipeRepository(db.recipeDao())
        repo.observeAll().first().forEach { repo.delete(it.id) }
        db.close()
        val db2 = open()
        val left = RoomRecipeRepository(db2.recipeDao()).observeAll().first()
        db2.close()
        file.delete()
        assertEquals(emptyList<Any>(), left)
    }
}
