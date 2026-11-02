package com.timemoredot.app

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** architecture.md 8.4: BOM 뒤 첫 줄이 헤더이고 저장한 V60 세션 행이 있다. */
class DemoCsvExportTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var uri: Uri? = null
    private val resolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    @Before fun setUp() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "demo_export_${System.nanoTime()}.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        Intents.init()
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
        rule.startFresh()
    }

    @After fun tearDown() {
        Intents.release()
        uri?.let { resolver.delete(it, null, null) }
        resetDemoState()
    }

    // 앱이 파일을 열기 전에는 행만 있고 파일이 없다
    private fun read() = runCatching { resolver.openInputStream(uri!!)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrDefault("")

    @Test fun exportsHeaderAndSavedV60Row() {
        rule.runV60AndSave()
        rule.onNodeWithCsv()
        rule.waitUntil(timeoutMillis = 10_000) { read().isNotEmpty() }
        val text = read()
        assertTrue("BOM", text.startsWith("﻿"))
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        assertEquals("session_id,started_at,recipe_name,dose_g,final_weight_g,duration_s,ratio,status,note", lines.first())
        assertTrue("V60 행: $lines", lines.drop(1).any { it.split(",").getOrNull(2) == "V60 기본" })
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithCsv() = onNode(hasContentDescription("CSV 내보내기")).performClick()
}
