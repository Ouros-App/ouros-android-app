package com.ouros.app.data.local

import android.app.Application
import org.junit.Assert.assertEquals
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
class OfflineStoreTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearDatabase() {
        context.deleteDatabase("ouros_offline.db")
    }

    @Test
    fun cache_roundTripsValuesAndIgnoresInvalidPayload() {
        val store = OfflineStore(context)
        val value = SampleValue(7L, "salvo")

        assertNull(store.cached<SampleValue>("missing"))
        store.cache("sample", value)
        assertEquals(value, store.cached<SampleValue>("sample"))

        OurosOfflineDatabase(context).putCache("invalid", "{not-json")
        assertNull(store.cached<SampleValue>("invalid"))
    }

    @Test
    fun queue_supportsOrderingAttemptsReplacementAndCompletion() {
        val store = OfflineStore(context)
        val firstId = store.enqueue("goals", "create", null, SampleValue(1L, "one"))
        val replacedId = store.enqueueReplacing("goals", "update", "42", SampleValue(2L, "old"))
        val replacementId = store.enqueueReplacing("goals", "update", "42", SampleValue(3L, "new"))

        val initial = store.pending("goals")
        assertEquals(listOf(firstId, replacementId), initial.map { it.id })
        assertTrue(initial.none { it.id == replacedId })
        assertEquals("new", store.decode(initial.last().payload, SampleValue::class.java).name)

        store.markAttempt(firstId)
        assertEquals(1, store.pending("goals").first().attempts)
        store.complete(firstId)
        assertEquals(listOf(replacementId), store.pending("goals").map { it.id })
    }

    @Test
    fun mappings_areScopedAndCanBeReplaced() {
        val store = OfflineStore(context)

        assertNull(store.mappedId("goals:1", -1L))
        store.saveMapping("goals:1", -1L, 10L)
        store.saveMapping("goals:2", -1L, 20L)
        assertEquals(10L, store.mappedId("goals:1", -1L))
        assertEquals(20L, store.mappedId("goals:2", -1L))

        store.saveMapping("goals:1", -1L, 11L)
        assertEquals(11L, store.mappedId("goals:1", -1L))
    }

    @Test
    fun databaseUpgrade_isIntentionallyNoOpForVersionOneSchema() {
        val database = OurosOfflineDatabase(context)
        database.onUpgrade(database.writableDatabase, 1, 1)
        database.putCache("still-usable", "payload")
        assertEquals("payload", database.getCache("still-usable"))
    }

    private data class SampleValue(val id: Long, val name: String)
}
