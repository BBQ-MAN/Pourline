package com.timemoredot.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val doseGrams: Double,
    val waterGrams: Double,
    val stepsJson: String,      // List<RecipeStep> JSON. 단계별 밸브 상태(valveOpen)도 여기에 들어간다
    val finishAtSec: Int?,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "brew_sessions", indices = [Index("startedAtEpochMs"), Index("status")])
data class BrewSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val startedAtEpochMs: Long,
    val recipeId: Long?,        // 외래 키 아님: 레시피를 지워도 기록은 남는다
    val recipeName: String?,
    val recipeJson: String?,
    val doseGrams: Double?,
    val finalWeightGrams: Double?,
    val durationMs: Long,
    val status: String,         // "in_progress" | "completed" | "interrupted"
    val note: String,
)

@Entity(
    tableName = "brew_samples",
    foreignKeys = [ForeignKey(BrewSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
class BrewSamplesEntity(@PrimaryKey val sessionId: Long, val blob: ByteArray)
