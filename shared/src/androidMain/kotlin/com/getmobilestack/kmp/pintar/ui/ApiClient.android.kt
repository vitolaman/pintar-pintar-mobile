package com.getmobilestack.kmp.pintar.ui

import android.util.Log
import com.getmobilestack.kmp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.IOException

internal actual fun createApiClient(): HttpClient = HttpClient(OkHttp) {
    if (BuildConfig.DEBUG) {
        engine {
            addInterceptor { chain ->
                val request = chain.request()
                // Query parameters and headers can contain credentials or personal data.
                val url = request.url.newBuilder().query(null).fragment(null).build()
                Log.d("PintarApi", "${request.method} $url")
                try {
                    chain.proceed(request).also { response ->
                        Log.d("PintarApi", "${response.code} ${request.method} $url")
                    }
                } catch (error: IOException) {
                    Log.e("PintarApi", "${request.method} $url failed: ${error.javaClass.simpleName}")
                    throw error
                }
            }
        }
    }
}
