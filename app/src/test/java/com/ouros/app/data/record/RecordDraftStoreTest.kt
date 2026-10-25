package com.ouros.app.data.record

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordDraftStoreTest {

    private lateinit var context: Context
    private lateinit var store: RecordDraftStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = RecordDraftStore(context)
        store.clear()
    }

    @Test
    fun load_whenEmpty_returnsNull() {
        assertNull(store.load())
    }

    @Test
    fun saveAndLoad_persistsDraftCorrectly() {
        val draft = RecordDraft(
            mode = RecordDraftMode.EDIT,
            step = 3,
            lotId = "12",
            lotCost = "4500.50",
            waterDate = "2026-10-25",
            hydrometerStart = "100.0",
            hydrometerEnd = "150.0",
            energyDate = "2026-10-25",
            energyKwh = "320.5",
            chickensEntered = "5000",
            chickensLeft = "10",
            chickensCurrent = "4990",
            waterRecordId = 1L,
            energyRecordId = 2L,
            chickenLeftRecordId = 3L
        )

        store.save(draft)

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(RecordDraftMode.EDIT, loaded?.mode)
        assertEquals(3, loaded?.step)
        assertEquals("12", loaded?.lotId)
        assertEquals("4500.50", loaded?.lotCost)
        assertEquals("2026-10-25", loaded?.waterDate)
        assertEquals("100.0", loaded?.hydrometerStart)
        assertEquals("150.0", loaded?.hydrometerEnd)
        assertEquals("2026-10-25", loaded?.energyDate)
        assertEquals("320.5", loaded?.energyKwh)
        assertEquals("5000", loaded?.chickensEntered)
        assertEquals("10", loaded?.chickensLeft)
        assertEquals("4990", loaded?.chickensCurrent)
        assertEquals(1L, loaded?.waterRecordId)
        assertEquals(2L, loaded?.energyRecordId)
        assertEquals(3L, loaded?.chickenLeftRecordId)
        assertTrue((loaded?.updatedAt ?: 0L) > 0L)
    }

    @Test
    fun clear_removesSavedDraft() {
        store.save(RecordDraft(lotId = "1"))
        assertNotNull(store.load())

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun load_whenCorruptedJson_returnsNullGracefully() {
        val prefs = context.applicationContext.getSharedPreferences("ouros_record_draft", Context.MODE_PRIVATE)
        prefs.edit().putString("draft", "{invalid_json").apply()

        assertNull(store.load())
    }
}
