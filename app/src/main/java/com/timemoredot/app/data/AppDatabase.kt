package com.timemoredot.app.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [RecipeEntity::class, BrewSessionEntity::class, BrewSamplesEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun brewDao(): BrewDao
}

/** 저장소와 시드가 같이 쓰는 Json. */
internal val DataJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** AppDatabase는 항상 이 함수로 만든다(테스트 포함): onCreate 시드가 빠지지 않게. */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder.addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = seedDefaultRecipes(db)
    }).build()

fun createAppDatabase(context: Context): AppDatabase =
    buildAppDatabase(Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "timemore.db"))

private fun seedDefaultRecipes(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    DefaultRecipes.all.forEach { r ->
        val cv = ContentValues().apply {
            put("name", r.name)
            put("doseGrams", r.doseGrams)
            put("waterGrams", r.waterGrams)
            put("stepsJson", DataJson.encodeToString(r.steps))
            if (r.finishAtSec != null) put("finishAtSec", r.finishAtSec) else putNull("finishAtSec")
            put("createdAtEpochMs", now)
        }
        db.insert("recipes", OnConflictStrategy.NONE, cv)
    }
}
