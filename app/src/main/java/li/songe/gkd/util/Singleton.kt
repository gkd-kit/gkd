package li.songe.gkd.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object Singleton {

    val json by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
    }

    val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json, ContentType.Any)
            }
            engine {
                clientCacheSize = 0
            }
        }
    }

}