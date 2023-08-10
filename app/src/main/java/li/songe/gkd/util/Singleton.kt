package li.songe.gkd.util

import blue.endless.jankson.Jankson
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale

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
    val json5: Jankson by lazy { Jankson.builder().build() }
    val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(json, ContentType.Any)
            }
            engine {
                connectTimeout = 10_000
                socketTimeout = 10_000
            }
        }
    }
    val simpleDateFormat by lazy { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val barcodeEncoder by lazy { BarcodeEncoder() }

}