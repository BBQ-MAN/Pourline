package com.timemoredot.app.data

import com.timemoredot.brew.BrewSample
import com.timemoredot.brew.FLAG_OVERLOAD
import com.timemoredot.brew.SampleBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class CsvExportTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun s(id: Long, status: SessionStatus = SessionStatus.Completed, dose: Double? = 15.0, final: Double? = 240.0,
                  name: String? = "V60", note: String = "", at: Long = 1_793_146_530_000) =
        SessionSummary(id, at, name, null, dose, final, 90_500, status, note)

    private fun sessions(vararg l: SessionSummary): Pair<String, Int> {
        val sb = StringBuilder()
        return sb.let { val n = CsvExport.writeSessions(l.toList(), seoul, it); it.toString() to n }
    }

    @Test fun sessionsHeaderBomAndFilter() {
        val (text, n) = sessions(s(1), s(2, SessionStatus.InProgress), s(3, SessionStatus.Interrupted))
        assertEquals(2, n)
        assertEquals(listOf(0xEF, 0xBB, 0xBF), text.toByteArray(Charsets.UTF_8).take(3).map { it.toInt() and 0xFF })
        assertTrue(text.removePrefix("﻿").startsWith("session_id,started_at,recipe_name,dose_g,final_weight_g,duration_s,ratio,status,note\r\n"))
        assertEquals(4, text.split("\r\n").size - 1 + 1)   // 헤더 + 2행 + 끝 빈 조각
        assertTrue(text.contains("\r\n1,"))
        assertTrue(!text.contains("\r\n2,"))
    }

    @Test fun sessionFormats() {
        val line = sessions(s(1)).first.removePrefix("﻿").split("\r\n")[1]
        // 1_793_146_530 s = 2026-10-28T00:15:30Z
        assertEquals("1,2026-10-28T09:15:30+09:00,V60,15.0,240.0,90.5,16.00,completed,", line)
        val blank = sessions(s(1, dose = null, final = null, name = null)).first.split("\r\n")[1]
        assertTrue(blank.endsWith(",,,90.5,,completed,"))
    }

    @Test fun rfc4180AndLocale() {
        val old = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val t = sessions(s(1, note = "a,\"b\"\nc")).first
            assertTrue(t.endsWith(",completed,\"a,\"\"b\"\"\nc\"\r\n"))
            assertTrue(t.contains(",15.0,240.0,90.5,16.00,"))
        } finally { Locale.setDefault(old) }
    }

    @Test fun samples() {
        val b = SampleBuffer()
        b.append(BrewSample(1500, 12.34f, 1.26f, 0))
        b.append(BrewSample(1600, Float.NaN, 0f, FLAG_OVERLOAD))
        val sb = StringBuilder()
        val old = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try { CsvExport.writeSamples(b, sb) } finally { Locale.setDefault(old) }
        val lines = sb.toString().removePrefix("﻿").split("\r\n")
        assertEquals("t_s,weight_g,flow_g_per_s,overload,disconnected", lines[0])
        assertEquals("1.500,12.3,1.3,0,0", lines[1])
        assertEquals("1.600,,0.0,1,0", lines[2])
        assertEquals("", lines[3])
    }
}
