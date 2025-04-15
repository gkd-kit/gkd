package li.songe.gkd.util

import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.tencent.mmkv.MMKV
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import li.songe.gkd.app
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.text.Collator
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


val kv by lazy { MMKV.mmkvWithID("kv") }

val json by lazy {
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

val keepNullJson by lazy {
    Json(from = json) {
        explicitNulls = true
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

val imageLoader by lazy {
    ImageLoader.Builder(app)
        .diskCache {
            DiskCache.Builder()
                .directory(coilCacheDir.toOkioPath())
                .maxSizePercent(0.1)
                .build()
        }
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(OkHttpNetworkFetcherFactory(
                callFactory = {
                    OkHttpClient.Builder()
                        .connectTimeout(30.seconds.toJavaDuration())
                        .readTimeout(30.seconds.toJavaDuration())
                        .writeTimeout(30.seconds.toJavaDuration())
                        .build()
                }
            ))
        }
        .build()
}


val collator by lazy { Collator.getInstance(Locale.CHINESE)!! }

