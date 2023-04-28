package li.songe.gkd.util

import blue.endless.jankson.Jankson
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object Singleton {

//    @OptIn(ExperimentalSerializationApi::class)
    val json by lazy {
        Json {
//            prettyPrint = true
//            prettyPrintIndent = "\u0020".repeat(2)
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
        }
    }

//    inline fun <reified T : Any> produce(data: T, block: (data: T) -> Unit): T {
//        val proxyData = Proxy.newProxyInstance(
//            T::class.java.classLoader,
//            arrayOf(),
//            InvocationHandler { proxy, method, args ->
//
//            }) as T
//        block(proxyData)
//        return proxyData
//    }

    val barcodeEncoder by lazy { BarcodeEncoder() }


}