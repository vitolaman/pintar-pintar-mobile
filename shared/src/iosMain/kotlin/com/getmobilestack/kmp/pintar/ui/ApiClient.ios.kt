package com.getmobilestack.kmp.pintar.ui

import io.ktor.client.HttpClient

internal actual fun createApiClient(): HttpClient = HttpClient()
