package li.gkd.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object NetworkClients {
    private val sharedClient = lazy { create() }
    val client: HttpClient get() = sharedClient.value
    fun close() {
        if (sharedClient.isInitialized()) sharedClient.value.close()
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
