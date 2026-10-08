package li.gkd.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object NetworkClients {
    private val sharedClient = lazy { create() }
    private val sharedProbeClient = lazy {
        HttpClient(OkHttp) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = 3_000 }
            engine {
                clientCacheSize = 0
                config { retryOnConnectionFailure(false) }
            }
        }
    }
    val client: HttpClient get() = sharedClient.value
    val probeClient: HttpClient get() = sharedProbeClient.value
    fun close() {
        if (sharedClient.isInitialized()) sharedClient.value.close()
        if (sharedProbeClient.isInitialized()) sharedProbeClient.value.close()
    }

    private fun create() = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
                ContentType.Any
            )
        }
        engine { clientCacheSize = 0 }
    }
}
