package com.timemoredot.brew

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeAndSamplesTest {

    private val v60 = recipe(50.0 to 30, 250.0 to 120)

    @Test fun finishSecFallsBackToLastStep() {
        assertEquals(120, v60.finishSec)
        assertEquals(210, recipe(50.0 to 5, 300.0 to 185, finishAtSec = 210).finishSec)
    }

    @Test fun jsonWithoutFinishAtSecDecodes() {
        val json = """{"id":3,"name":"V60","doseGrams":15.0,"waterGrams":250.0,"steps":[{"name":"a","targetGrams":60.0,"targetAtSec":10}]}"""
        val r = Json.decodeFromString<Recipe>(json)
        assertNull(r.finishAtSec)
        assertEquals(10, r.finishSec)
    }

    @Test fun jsonRoundTrip() {
        for (r in listOf(v60, recipe(240.0 to 10, finishAtSec = 120, dose = 20.0))) {
            val encoded = Json.encodeToString(Recipe.serializer(), r)
            assertFalse(encoded.contains("\"finishSec\""))
            assertEquals(r, Json.decodeFromString<Recipe>(encoded))
        }
    }

    @Test fun plannedGramsAtInterpolatesAndClamps() {
        assertEquals(0.0, v60.plannedGramsAt(-1.0), 1e-9)
        assertEquals(0.0, v60.plannedGramsAt(0.0), 1e-9)
        assertEquals(25.0, v60.plannedGramsAt(15.0), 1e-9)
        assertEquals(50.0, v60.plannedGramsAt(30.0), 1e-9)
        assertEquals(150.0, v60.plannedGramsAt(75.0), 1e-9)
        assertEquals(250.0, v60.plannedGramsAt(120.0), 1e-9)
        assertEquals(250.0, v60.plannedGramsAt(500.0), 1e-9)
    }

    @Test fun plannedSecForInterpolatesAndClamps() {
        assertEquals(0.0, v60.plannedSecFor(-5.0), 1e-9)
        assertEquals(0.0, v60.plannedSecFor(0.0), 1e-9)
        assertEquals(15.0, v60.plannedSecFor(25.0), 1e-9)
        assertEquals(30.0, v60.plannedSecFor(50.0), 1e-9)
        assertEquals(75.0, v60.plannedSecFor(150.0), 1e-9)
        assertEquals(120.0, v60.plannedSecFor(250.0), 1e-9)
        assertEquals(120.0, v60.plannedSecFor(400.0), 1e-9)
    }

    @Test fun bufferCapacity() {
        val b = SampleBuffer(3)
        repeat(3) { assertTrue(b.append(BrewSample(it * 100, 1f, 0f, 0))) }
        assertFalse(b.append(BrewSample(300, 1f, 0f, 0)))
        assertEquals(3, b.size)
        b.clear()
        assertEquals(0, b.size)
    }

    @Test fun blobRoundTrip() {
        val b = SampleBuffer(10)
        val input = listOf(
            BrewSample(0, 0f, 0f, 0),
            BrewSample(100, 12.3f, 3.5f, 0),
            BrewSample(200, Float.NaN, 3.5f, FLAG_OVERLOAD),
            BrewSample(300, 12.3f, 0f, FLAG_DISCONNECTED),
        )
        input.forEach { b.append(it) }
        val blob = b.toBlob()
        assertEquals(0x01.toByte(), blob[0])
        assertEquals(1 + 13 * input.size, blob.size)
        val back = SampleBuffer.fromBlob(blob)
        assertEquals(input.size, back.size)
        input.forEachIndexed { i, s ->
            assertEquals(s.offsetMs, back.offsetMs(i))
            if (s.weightGrams.isNaN()) assertTrue(back.weightGrams(i).isNaN()) else assertEquals(s.weightGrams, back.weightGrams(i), 0f)
            assertEquals(s.flowGramsPerSec, back.flowGramsPerSec(i), 0f)
            assertEquals(s.flags, back.flags(i))
        }
        assertEquals(0, SampleBuffer.fromBlob(SampleBuffer().toBlob()).size)
    }

    @Test fun badBlobsThrow() {
        val good = SampleBuffer(1).apply { append(BrewSample(0, 1f, 0f, 0)) }.toBlob()
        for (bad in listOf(good.copyOf().also { it[0] = 2 }, ByteArray(0), good.copyOf(good.size - 1))) {
            try {
                SampleBuffer.fromBlob(bad)
                throw AssertionError("expected IllegalArgumentException for ${bad.size} bytes")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private fun buffer(vararg s: Pair<Int, Int>, weight: (Int) -> Float = { 1f }) =
        SampleBuffer().apply { s.forEach { (offset, flags) -> append(BrewSample(offset, weight(offset), 0f, flags)) } }

    @Test fun gapsFromSpacing() {
        assertEquals(listOf(200..900), buffer(0 to 0, 100 to 0, 200 to 0, 900 to 0, 1000 to 0).gaps())
        assertTrue(buffer(0 to 0, 500 to 0, 1000 to 0).gaps().isEmpty())   // 정확히 500 ms는 공백 아님
    }

    @Test fun gapsIncludeFlaggedRuns() {
        assertEquals(listOf(400..700), buffer(0 to 0, 100 to 0, 200 to 0, 300 to 0, 400 to 0, 500 to FLAG_DISCONNECTED, 600 to FLAG_OVERLOAD, 700 to 0).gaps())
        assertEquals(listOf(100..3000), buffer(0 to 0, 100 to 0, 200 to FLAG_DISCONNECTED, 3000 to 0, 3100 to 0).gaps())
    }

    @Test fun reachTimesReturnNullForUnreachedSteps() {
        val weights = mapOf(0 to 0f, 100 to 20f, 200 to 300f, 300 to 49.5f, 400 to 100f)
        val b = buffer(0 to 0, 100 to 0, 200 to FLAG_OVERLOAD, 300 to 0, 400 to 0) { weights.getValue(it) }
        assertEquals(listOf(300, null), v60.reachTimesMs(b))
    }
}
