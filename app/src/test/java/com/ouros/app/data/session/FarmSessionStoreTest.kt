package com.ouros.app.data.session

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FarmSessionStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { key?.let { pending[it] = values } }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
            override fun remove(key: String?): SharedPreferences.Editor = apply { key?.let { removed.add(it) } }
            override fun clear(): SharedPreferences.Editor = apply { data.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                removed.forEach { data.remove(it) }
                pending.forEach { (k, v) -> data[k] = v }
            }
        }
    }

    @Test
    fun nullContext_safeFallbackReturnsNull() {
        val store = FarmSessionStore(context = null, preferences = null)
        assertNull(store.ownerId())
        assertNull(store.farmId())
        store.saveAccount(1L, 2L)
        store.clear()
        assertNull(store.ownerId())
        assertNull(store.farmId())
    }

    @Test
    fun saveAccount_savesAndRetrievesAccountIds() {
        val fakePrefs = FakeSharedPreferences()
        val store = FarmSessionStore(context = null, preferences = fakePrefs)
        store.clear()

        assertNull(store.ownerId())
        assertNull(store.farmId())

        store.saveAccount(100L, 200L)
        assertEquals(100L, store.ownerId())
        assertEquals(200L, store.farmId())

        store.saveAccount(null, 300L)
        assertNull(store.ownerId())
        assertEquals(300L, store.farmId())

        store.saveAccount(400L, null)
        assertEquals(400L, store.ownerId())
        assertNull(store.farmId())

        store.clear()
        assertNull(store.ownerId())
        assertNull(store.farmId())
    }

    @Test
    fun storedId_ignoresInvalidIdZeroOrNegative() {
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.edit().putLong("owner_id", 0L).putLong("farm_id", -1L).apply()
        val store = FarmSessionStore(context = null, preferences = fakePrefs)

        assertNull(store.ownerId())
        assertNull(store.farmId())
    }
}
