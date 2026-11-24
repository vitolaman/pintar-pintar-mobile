package com.getmobilestack.kmp.pintar.account

import com.liftric.kvault.KVault
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SessionStore {
    fun load(): AccountSession?
    fun save(session: AccountSession)
    fun clear()
}

interface SecureSessionBackend {
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
    fun contains(key: String): Boolean
    fun delete(key: String): Boolean
}

class VaultSessionBackend(private val vault: KVault) : SecureSessionBackend {
    override fun read(key: String) = vault.string(key)
    override fun write(key: String, value: String) = vault.set(key, value)
    override fun contains(key: String) = vault.existsObject(key)
    override fun delete(key: String) = vault.deleteObject(key)
}

/** Stores the whole session as a single atomic encrypted entry. */
class SecureSessionStore(private val backend: SecureSessionBackend) : SessionStore {
    constructor(vault: KVault) : this(VaultSessionBackend(vault))
    private val key = "pintar.account.session.v1"
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): AccountSession? {
        val value = backend.read(key) ?: return null
        val session = try {
            json.decodeFromString<AccountSession>(value)
        } catch (_: SerializationException) {
            clear()
            return null
        } catch (_: IllegalArgumentException) {
            clear()
            return null
        }
        if (session.token.isBlank()) {
            clear()
            return null
        }
        return session
    }

    override fun save(session: AccountSession) {
        require(session.token.isNotBlank())
        check(backend.write(key, json.encodeToString(session))) { "Unable to save session securely" }
    }

    override fun clear() {
        if (backend.contains(key)) {
            check(backend.delete(key)) { "Unable to remove stored session" }
        }
    }
}
