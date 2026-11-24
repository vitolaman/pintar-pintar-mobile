package com.getmobilestack.kmp.pintar.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SessionStoreTest {
    private class FakeBackend : SecureSessionBackend {
        val entries = mutableMapOf<String, String>()
        var failWrite = false
        var failDelete = false
        override fun read(key: String) = entries[key]
        override fun write(key: String, value: String): Boolean {
            if (failWrite) return false
            entries[key] = value
            return true
        }
        override fun contains(key: String) = key in entries
        override fun delete(key: String): Boolean {
            if (failDelete) return false
            entries.remove(key)
            return true
        }
    }

    private val session = AccountSession("secret", AccountUser("1", "User", "user@example.com"))

    @Test
    fun `restores saved session after store recreation`() {
        val backend = FakeBackend()
        SecureSessionStore(backend).save(session)
        assertEquals(session, SecureSessionStore(backend).load())
    }

    @Test
    fun `clears persisted session and allows repeated clear`() {
        val backend = FakeBackend()
        val store = SecureSessionStore(backend)
        store.save(session)
        store.clear()
        store.clear()
        assertNull(store.load())
        assertFalse(backend.entries.isNotEmpty())
    }

    @Test
    fun `removes corrupt or blank session`() {
        val backend = FakeBackend()
        val store = SecureSessionStore(backend)
        store.save(session)
        val key = backend.entries.keys.single()
        backend.entries[key] = "invalid json"
        assertNull(store.load())
        assertFalse(backend.contains(key))
        backend.entries[key] = """{"token":"","user":{"id":"1","name":"U","email":"u@e.com"}}"""
        assertNull(store.load())
        assertFalse(backend.contains(key))
    }

    @Test
    fun `propagates failed secure writes and deletes`() {
        val backend = FakeBackend()
        val store = SecureSessionStore(backend)
        backend.failWrite = true
        assertFailsWith<IllegalStateException> { store.save(session) }
        backend.failWrite = false
        store.save(session)
        backend.failDelete = true
        assertFailsWith<IllegalStateException> { store.clear() }
        assertEquals(session, store.load())
    }
}
