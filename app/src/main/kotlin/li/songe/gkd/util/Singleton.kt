package li.songe.gkd.util

import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import com.tencent.mmkv.MMKV
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import li.songe.gkd.app
import okhttp3.OkHttpClient
import java.text.Collator
import java.time.Duration
import java.util.Locale


val kv by lazy { MMKV.mmkvWithID("kv")!! }

@OptIn(ExperimentalSerializationApi::class)
val json by lazy {
    Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

val keepNullJson by lazy {
    Json {
        isLenient = true
        ignoreUnknownKeys = true
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

val imageLoader by lazy {
    ImageLoader.Builder(app)
        .okHttpClient(
            OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build()
        )
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }.diskCache {
            DiskCache.Builder().directory(imageCacheDir).build()
        }.build()
}


val collator by lazy { Collator.getInstance(Locale.CHINESE)!! }

