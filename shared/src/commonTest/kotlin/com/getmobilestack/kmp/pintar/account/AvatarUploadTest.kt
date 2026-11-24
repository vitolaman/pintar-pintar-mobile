package com.getmobilestack.kmp.pintar.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AvatarUploadTest {
    @Test fun `uploads single signed part and completes without leaking bearer`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "storage.example.com") {
                assertNull(request.headers[HttpHeaders.Authorization])
                respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "etag"))
            } else {
                assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
                when (request.url.encodedPath) {
                    "/api/v1/upload/initiate" -> {
                        assertEquals("""{"fileName":"photo.png","contentType":"image/png"}""", (request.body as TextContent).text)
                        respond("""{"uploadId":"upload","key":"key"}""")
                    }
                    "/api/v1/upload/presigned-urls" -> {
                        assertEquals("""{"uploadId":"upload","key":"key","partsCount":1}""", (request.body as TextContent).text)
                        respond("""[{"partNumber":1,"url":"https://storage.example.com/part"}]""")
                    }
                    "/api/v1/upload/complete" -> {
                        assertEquals("""{"uploadId":"upload","key":"key","parts":[{"PartNumber":1,"ETag":"etag"}]}""", (request.body as TextContent).text)
                        respond("""{"asset_id":"asset"}""")
                    }
                    else -> error("Unexpected path")
                }
            }
        })
        try { assertEquals("asset", ApiAvatarUploader(client, "https://example.com").upload("secret", "photo.png", byteArrayOf(1))) }
        finally { client.close() }
    }

    @Test fun `rejects insecure signed url`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/upload/initiate" -> respond("""{"uploadId":"upload","key":"key"}""")
                "/api/v1/upload/presigned-urls" -> respond("""[{"partNumber":1,"url":"http://storage.example.com/part"}]""")
                else -> error("Unexpected request")
            }
        })
        try { assertFailsWith<IllegalArgumentException> { ApiAvatarUploader(client, "https://example.com").upload("secret", "photo.jpg", byteArrayOf(1)) }; Unit }
        finally { client.close() }
    }
}
