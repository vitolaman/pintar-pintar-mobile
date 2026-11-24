package com.getmobilestack.kmp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.getmobilestack.kmp.pintar.ui.PintarApp
import com.getmobilestack.kmp.pintar.account.SecureSessionStore
import com.getmobilestack.kmp.pintar.account.SessionStore
import com.getmobilestack.kmp.pintar.account.AccountSession
import com.liftric.kvault.KVault

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionStore = object : SessionStore {
            private val delegate by lazy { SecureSessionStore(KVault(applicationContext, "pintar_secure_session")) }
            override fun load() = delegate.load()
            override fun save(session: AccountSession) = delegate.save(session)
            override fun clear() = delegate.clear()
        }
        setContent {
            PintarApp(sessionStore)
        }
    }
}
