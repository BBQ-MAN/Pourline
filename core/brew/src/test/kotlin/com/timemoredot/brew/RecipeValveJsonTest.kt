package com.timemoredot.brew

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 7.1·8.3: 밸브 상태는 RecipeStep.valveOpen에만 저장되고 valveSchedule은 파생 게터다. */
class RecipeValveJsonTest {
    private val formats = listOf(Json, Json { ignoreUnknownKeys = true; encodeDefaults = true })

    // M6 저장 형식(stepsJson·recipeJson)의 valveOpen 없는 옛 문자열
    private val oldSteps = """[{"name":"a","targetGrams":60.0,"targetAtSec":10},{"name":"b","targetGrams":250.0,"targetAtSec":90}]"""
    private val oldRecipe = """{"id":3,"name":"V60","doseGrams":15.0,"waterGrams":250.0,"steps":[{"name":"a","targetGrams":60.0,"targetAtSec":10}]}"""
    private val oldRecipeWithFinish = """{"id":3,"name":"V60","doseGrams":15.0,"waterGrams":250.0,"steps":[{"name":"a","targetGrams":60.0,"targetAtSec":10}],"finishAtSec":120}"""

    @Test fun jsonWithoutValveOpenDecodesToNoValve() {
        formats.forEach { json ->
            val steps = json.decodeFromString(ListSerializer(RecipeStep.serializer()), oldSteps)
            assertEquals(2, steps.size)
            assertTrue(steps.all { it.valveOpen == null })
            listOf(oldRecipe, oldRecipeWithFinish).forEach { s ->
                val r = json.decodeFromString<Recipe>(s)
                assertTrue(r.steps.all { it.valveOpen == null })
                assertEquals(emptyList<ValveEvent>(), r.valveSchedule)
            }
        }
    }

    @Test fun abortedFix4ValveScheduleKeyIsIgnoredByDataJson() {
        val lenient = formats[1]
        val s = oldRecipe.dropLast(1) + ""","valveSchedule":[{"atSec":0,"open":true}]}"""
        assertEquals(emptyList<ValveEvent>(), lenient.decodeFromString<Recipe>(s).valveSchedule)
    }

    @Test fun roundTripKeepsStepValves() {
        formats.forEach { json ->
            listOf(hario(), devil(), hario().withoutValve(), devil().withoutValve()).forEach { r ->
                val text = json.encodeToString(Recipe.serializer(), r)
                assertEquals(r, json.decodeFromString<Recipe>(text))
                assertFalse(text, json.parseToJsonElement(text).jsonObject.containsKey("valveSchedule"))
            }
        }
    }

    @Test fun valveScheduleIsNotASerializedField() {
        val d = Recipe.serializer().descriptor
        assertFalse((0 until d.elementsCount).any { d.getElementName(it) == "valveSchedule" })
    }
}
