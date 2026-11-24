package com.getmobilestack.kmp

import androidx.compose.ui.window.ComposeUIViewController
import com.getmobilestack.kmp.pintar.ui.PintarApp
import com.getmobilestack.kmp.pintar.account.SecureSessionStore
import com.getmobilestack.kmp.pintar.account.SessionStore
import com.getmobilestack.kmp.pintar.account.AccountSession
import com.liftric.kvault.KVault

fun createRootViewController() =
    ComposeUIViewController {
        val store = androidx.compose.runtime.remember {
            object : SessionStore {
                private val delegate by lazy {
                    SecureSessionStore(KVault(serviceName = "id.pintarpintar.session", accessibility = KVault.Accessible.WhenUnlockedThisDeviceOnly))
                }
                override fun load() = delegate.load()
                override fun save(session: AccountSession) = delegate.save(session)
                override fun clear() = delegate.clear()
            }
        }
        PintarApp(store)
    }
