package com.timemoredot.brew

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val FLAG_OVERLOAD = 1
const val FLAG_DISCONNECTED = 2

/** weightGrams는 과부하면 Float.NaN. offsetMs는 일시정지를 뺀 앱 타이머 경과 ms. */
data class BrewSample(val offsetMs: Int, val weightGrams: Float, val flowGramsPerSec: Float, val flags: Int)

private const val BLOB_VERSION: Byte = 0x01
private const val RECORD_BYTES = 13   // int32 offsetMs, float32 weight, float32 flow, uint8 flags (little-endian)

/** 고정 용량 원시 배열 버퍼. 가득 차면 append가 false를 돌려준다. 메인 스레드 전용, 스레드 경계를 넘기지 않는다. */
class SampleBuffer(val capacity: Int = 36_000) {   // 10 Hz × 3600 s
    private val offsets = IntArray(capacity)
    private val weights = FloatArray(capacity)
    private val flows = FloatArray(capacity)
    private val flagBytes = ByteArray(capacity)

    var size: Int = 0
        private set

    fun offsetMs(i: Int): Int = offsets[checkIndex(i)]
    fun weightGrams(i: Int): Float = weights[checkIndex(i)]
    fun flowGramsPerSec(i: Int): Float = flows[checkIndex(i)]
    fun flags(i: Int): Int = flagBytes[checkIndex(i)].toInt() and 0xFF

    fun append(sample: BrewSample): Boolean {
        if (size == capacity) return false
        offsets[size] = sample.offsetMs
        weights[size] = sample.weightGrams
        flows[size] = sample.flowGramsPerSec
        flagBytes[size] = sample.flags.toByte()
        size++
        return true
    }

    fun clear() { size = 0 }

    fun toBlob(): ByteArray {
        val buf = ByteBuffer.allocate(1 + RECORD_BYTES * size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(BLOB_VERSION)
        for (i in 0 until size) buf.putInt(offsets[i]).putFloat(weights[i]).putFloat(flows[i]).put(flagBytes[i])
        return buf.array()
    }

    private fun checkIndex(i: Int): Int {
        if (i !in 0 until size) throw IndexOutOfBoundsException("index $i, size $size")
        return i
    }

    companion object {
        /** 모르는 버전 바이트나 맞지 않는 길이는 IllegalArgumentException (호출자는 NoSamples로 처리). */
        fun fromBlob(blob: ByteArray): SampleBuffer {
            require(blob.isNotEmpty() && blob[0] == BLOB_VERSION) { "unknown sample blob version" }
            require((blob.size - 1) % RECORD_BYTES == 0) { "sample blob length ${blob.size} is not 1 + 13n" }
            val n = (blob.size - 1) / RECORD_BYTES
            val buf = ByteBuffer.wrap(blob, 1, blob.size - 1).order(ByteOrder.LITTLE_ENDIAN)
            return SampleBuffer(n).apply {
                repeat(n) { append(BrewSample(buf.int, buf.float, buf.float, buf.get().toInt() and 0xFF)) }
            }
        }
    }
}

/** 값 없는 구간(ms 범위): 이웃 샘플 간격 > minGapMs, 또는 플래그 샘플이 이어진 구간(앞뒤 정상 샘플까지). 겹치면 합친다. */
fun SampleBuffer.gaps(minGapMs: Int = 500): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var i = 0
    while (i < size) {
        if (flags(i) != 0) {
            var j = i
            while (j + 1 < size && flags(j + 1) != 0) j++
            ranges += offsetMs(maxOf(i - 1, 0))..offsetMs(minOf(j + 1, size - 1))
            i = j + 1
        } else {
            if (i > 0 && offsetMs(i) - offsetMs(i - 1) > minGapMs) ranges += offsetMs(i - 1)..offsetMs(i)
            i++
        }
    }
    val merged = mutableListOf<IntRange>()
    for (r in ranges.sortedBy { it.first }) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last) merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
        else merged += r
    }
    return merged
}

/** 단계 도달 판정 여유(g). 엔진 단계 판정(BrewConfig.stepToleranceGrams 기본값), reachTimesMs 기본값,
 *  S6·S10 요약판 표식 "도달"이 모두 이 상수를 쓴다(architecture.md 7.1). */
const val STEP_TOLERANCE_GRAMS: Double = 1.0

/** 단계마다 정상 샘플이 targetGrams − toleranceGrams에 처음 도달한 offsetMs, 못 했으면 null. */
fun Recipe.reachTimesMs(samples: SampleBuffer, toleranceGrams: Double = STEP_TOLERANCE_GRAMS): List<Int?> = steps.map { step ->
    (0 until samples.size)
        .firstOrNull { samples.flags(it) == 0 && samples.weightGrams(it) >= step.targetGrams - toleranceGrams }
        ?.let(samples::offsetMs)
}
