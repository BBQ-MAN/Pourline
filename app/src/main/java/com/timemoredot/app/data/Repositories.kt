package com.timemoredot.app.data

import androidx.room.withTransaction
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.RecipeStep
import com.timemoredot.brew.SampleBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

enum class SessionStatus(val db: String) { InProgress("in_progress"), Completed("completed"), Interrupted("interrupted") }

data class SessionSummary(
    val id: Long, val startedAtEpochMs: Long, val recipeName: String?, val recipe: Recipe?, val doseGrams: Double?,
    val finalWeightGrams: Double?, val durationMs: Long, val status: SessionStatus, val note: String,
) {
    val ratio: Double? get() = if (doseGrams != null && doseGrams > 0 && finalWeightGrams != null) finalWeightGrams / doseGrams else null
}

class SessionDraft(
    val id: Long?,
    val startedAtEpochMs: Long,
    val recipe: Recipe?,
    val finalWeightGrams: Double?,
    val durationMs: Long,
    val samplesBlob: ByteArray,
)

interface RecipeRepository {
    fun observeAll(): Flow<List<Recipe>>
    suspend fun get(id: Long): Recipe?
    suspend fun upsert(recipe: Recipe): Long
    suspend fun delete(id: Long)
}

interface BrewRepository {
    fun observeSessions(): Flow<List<SessionSummary>>
    suspend fun summary(id: Long): SessionSummary?
    suspend fun samples(id: Long): SampleBuffer?
    suspend fun saveDraft(draft: SessionDraft): Long
    suspend fun findDraft(excludingId: Long?): SessionSummary?
    suspend fun finish(id: Long, doseGrams: Double?, note: String, status: SessionStatus)
    suspend fun deleteSession(id: Long)
    suspend fun updateNote(id: Long, note: String)
}

internal fun RecipeEntity.toRecipe() = Recipe(
    id, name, doseGrams, waterGrams, DataJson.decodeFromString<List<RecipeStep>>(stepsJson), finishAtSec,
)

class RoomRecipeRepository(private val dao: RecipeDao) : RecipeRepository {
    override fun observeAll() = dao.observeAll().map { l -> l.map { it.toRecipe() } }.flowOn(Dispatchers.IO)

    override suspend fun get(id: Long) = withContext(Dispatchers.IO) { dao.get(id)?.toRecipe() }

    override suspend fun upsert(recipe: Recipe): Long = withContext(Dispatchers.IO) {
        val created = if (recipe.id != 0L) dao.get(recipe.id)?.createdAtEpochMs else null
        dao.insert(
            RecipeEntity(
                recipe.id, recipe.name, recipe.doseGrams, recipe.waterGrams,
                DataJson.encodeToString(recipe.steps), recipe.finishAtSec,
                created ?: System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
}

class RoomBrewRepository(private val database: AppDatabase, private val dao: BrewDao) : BrewRepository {
    private fun BrewSessionEntity.toSummary() = SessionSummary(
        id, startedAtEpochMs, recipeName,
        recipeJson?.let { runCatching { DataJson.decodeFromString<Recipe>(it) }.getOrNull() },
        doseGrams, finalWeightGrams, durationMs,
        SessionStatus.entries.firstOrNull { it.db == status } ?: SessionStatus.Interrupted, note,
    )

    override fun observeSessions() = dao.observeSessions().map { l -> l.map { it.toSummary() } }.flowOn(Dispatchers.IO)

    override suspend fun summary(id: Long) = withContext(Dispatchers.IO) { dao.session(id)?.toSummary() }

    override suspend fun samples(id: Long): SampleBuffer? = withContext(Dispatchers.IO) {
        dao.blob(id)?.let { runCatching { SampleBuffer.fromBlob(it) }.getOrNull() }
    }

    override suspend fun saveDraft(draft: SessionDraft): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            // 기존 id는 in_progress 행만 갱신한다. 삭제·완료된 행을 되살리거나 dose·note를 덮지 않는다(architecture.md 8.3)
            if (draft.id != null && dao.session(draft.id)?.status != SessionStatus.InProgress.db) return@withTransaction draft.id
            val r = draft.recipe
            val rowId = dao.upsertSession(
                BrewSessionEntity(
                    draft.id ?: 0, draft.startedAtEpochMs, r?.id, r?.name, r?.let { DataJson.encodeToString(it) },
                    null, draft.finalWeightGrams, draft.durationMs, SessionStatus.InProgress.db, "",
                ),
            )
            val id = draft.id ?: rowId
            dao.upsertSamples(BrewSamplesEntity(id, draft.samplesBlob))
            id
        }
    }

    override suspend fun findDraft(excludingId: Long?) = withContext(Dispatchers.IO) { dao.findDraft(excludingId)?.toSummary() }

    override suspend fun finish(id: Long, doseGrams: Double?, note: String, status: SessionStatus) =
        withContext(Dispatchers.IO) { dao.finish(id, doseGrams, note, status.db) }

    override suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    override suspend fun updateNote(id: Long, note: String) = withContext(Dispatchers.IO) { dao.updateNote(id, note) }
}
