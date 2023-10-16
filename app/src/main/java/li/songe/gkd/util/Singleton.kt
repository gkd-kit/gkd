package li.songe.gkd.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 所有单例及其属性必须是不可变属性,以保持多进程下的配置统一性
 */
object Singleton {

    val json by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    val omitJson by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = false
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