package org.himstedt.fireside.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The API key has to travel with the repository, not with the process. Getting
 * this wrong is invisible until a fresh install tries to connect: the key is
 * published only once a config is *saved*, and saving only happens after the
 * test call that needs the key — so the request went out with no `x-api-key` and
 * Immich answered 401 "Authentication required", which the setup screen reports
 * as "Immich rejected the API key".
 */
class RepositoryAuthTest {

    /**
     * Answers every request itself and records what it was given. It has to be
     * added *after* the auth interceptor, so it sees the request as it leaves
     * the process rather than as it entered the chain.
     */
    private fun terminalRecorder(seen: MutableList<Request>): Interceptor =
        Interceptor { chain ->
            seen += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }

    private fun OkHttpClient.fire(url: String) {
        newCall(Request.Builder().url(url).build()).execute().close()
    }

    @Test
    fun `the interceptor sends the key it is given`() {
        val seen = mutableListOf<Request>()
        val client = authenticatedClient(OkHttpClient.Builder().build()) { "KEY123" }
            .newBuilder()
            .addInterceptor(terminalRecorder(seen))
            .build()

        client.fire("https://immich.test/api/albums")

        assertEquals("KEY123", seen.single().header("x-api-key"))
        assertEquals("application/json", seen.single().header("Accept"))
    }

    @Test
    fun `the key is read per request, so a runtime change is picked up`() {
        val seen = mutableListOf<Request>()
        var key = "FIRST"
        val client = authenticatedClient(OkHttpClient.Builder().build()) { key }
            .newBuilder()
            .addInterceptor(terminalRecorder(seen))
            .build()

        client.fire("https://immich.test/api/albums")
        key = "SECOND"
        client.fire("https://immich.test/api/albums")

        assertEquals(listOf("FIRST", "SECOND"), seen.map { it.header("x-api-key") })
    }

    @Test
    fun `a repository authenticates from the config it was built with`() {
        val seen = mutableListOf<Request>()
        val repository = ImmichRepository(
            ImmichConfig(baseUrl = "https://immich.test", apiKey = "KEY123"),
        )

        repository.okHttp.newBuilder()
            .addInterceptor(terminalRecorder(seen))
            .build()
            .fire("https://immich.test/api/albums")

        assertEquals("KEY123", seen.single().header("x-api-key"))
    }

    @Test
    fun `an injected client is wrapped with the repository's key, not left bare`() {
        val seen = mutableListOf<Request>()
        // Stands in for the process-wide client, which carries no credential.
        val shared = OkHttpClient.Builder().build()
        val repository = ImmichRepository(
            ImmichConfig(baseUrl = "https://immich.test", apiKey = "TYPED-BY-USER"),
            shared,
        )

        repository.okHttp.newBuilder()
            .addInterceptor(terminalRecorder(seen))
            .build()
            .fire("https://immich.test/api/albums")

        // This is the regression: the injected client used to be used verbatim,
        // so the typed key never reached the server.
        assertEquals("TYPED-BY-USER", seen.single().header("x-api-key"))
    }
}
