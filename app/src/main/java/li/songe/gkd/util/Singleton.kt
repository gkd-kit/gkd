package li.songe.gkd.util

import blue.endless.jankson.Jankson
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale

object Singleton {

    val json by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    }
    val json5: Jankson by lazy { Jankson.builder().build() }
    val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json, ContentType.Any)
            }
            install(HttpTimeout){
                connectTimeoutMillis = 3000
            }
        }
    }
    val simpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val barcodeEncoder by lazy { BarcodeEncoder() }

}