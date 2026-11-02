package com.timemoredot.app.data

import com.timemoredot.brew.FLAG_DISCONNECTED
import com.timemoredot.brew.FLAG_OVERLOAD
import com.timemoredot.brew.SampleBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 사용자가 고른 내보내기 대상 하나(architecture.md 8.4). ViewModel은 이것만 받는다(Uri·ContentResolver 없음).
 *  열기·쓰기 실패는 예외로 던진다. 테스트는 StringBuilder에 쓰거나 예외를 던지는 람다를 넘긴다. */
fun interface CsvTarget { suspend fun write(block: (Appendable) -> Unit) }

object CsvExport {
    private val BOM = 0xFEFF.toChar().toString()
    private const val EOL = "\r\n"

    fun writeSessions(sessions: List<SessionSummary>, zone: ZoneId, out: Appendable): Int {
        out.append(BOM).append("session_id,started_at,recipe_name,dose_g,final_weight_g,duration_s,ratio,status,note").append(EOL)
        var n = 0
        for (s in sessions) {
            if (s.status == SessionStatus.InProgress) continue
            val at = Instant.ofEpochMilli(s.startedAtEpochMs).atZone(zone).truncatedTo(ChronoUnit.SECONDS)
            row(
                out, s.id.toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at), s.recipeName.orEmpty(),
                s.doseGrams?.let { num(it, 1) }.orEmpty(), s.finalWeightGrams?.let { num(it, 1) }.orEmpty(),
                num(s.durationMs / 1000.0, 1), s.ratio?.let { num(it, 2) }.orEmpty(), s.status.db, s.note,
            )
            n++
        }
        return n
    }

    fun writeSamples(samples: SampleBuffer, out: Appendable) {
        out.append(BOM).append("t_s,weight_g,flow_g_per_s,overload,disconnected").append(EOL)
        for (i in 0 until samples.size) {
            val w = samples.weightGrams(i)
            val f = samples.flags(i)
            row(
                out, num(samples.offsetMs(i) / 1000.0, 3), if (w.isNaN()) "" else num(w.toDouble(), 1),
                num(samples.flowGramsPerSec(i).toDouble(), 1),
                if (f and FLAG_OVERLOAD != 0) "1" else "0", if (f and FLAG_DISCONNECTED != 0) "1" else "0",
            )
        }
    }

    private fun num(v: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)

    private fun row(out: Appendable, vararg cells: String) {
        cells.forEachIndexed { i, c -> if (i > 0) out.append(','); out.append(esc(c)) }
        out.append(EOL)
    }

    private fun esc(c: String) =
        if (c.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + c.replace("\"", "\"\"") + "\"" else c
}
