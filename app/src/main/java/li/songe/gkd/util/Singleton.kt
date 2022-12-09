package li.songe.gkd.util

import blue.endless.jankson.Jankson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

object Singleton {

    val json by lazy {
        Json {
            prettyPrint = true
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


}