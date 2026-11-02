package com.timemoredot.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name, id")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: Long): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecipeEntity): Long

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BrewDao {
    @Query("SELECT * FROM brew_sessions WHERE status != 'in_progress' ORDER BY startedAtEpochMs DESC, id DESC")
    fun observeSessions(): Flow<List<BrewSessionEntity>>

    @Query("SELECT * FROM brew_sessions WHERE id = :id")
    suspend fun session(id: Long): BrewSessionEntity?

    @Query("SELECT blob FROM brew_samples WHERE sessionId = :id")
    suspend fun blob(id: Long): ByteArray?

    @Query(
        "SELECT * FROM brew_sessions WHERE status = 'in_progress' AND (:excludingId IS NULL OR id != :excludingId) " +
            "ORDER BY startedAtEpochMs DESC, id DESC LIMIT 1",
    )
    suspend fun findDraft(excludingId: Long?): BrewSessionEntity?

    @Upsert
    suspend fun upsertSession(entity: BrewSessionEntity): Long

    @Upsert
    suspend fun upsertSamples(entity: BrewSamplesEntity)

    @Query("UPDATE brew_sessions SET doseGrams = :dose, note = :note, status = :status WHERE id = :id")
    suspend fun finish(id: Long, dose: Double?, note: String, status: String)

    @Query("UPDATE brew_sessions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM brew_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
