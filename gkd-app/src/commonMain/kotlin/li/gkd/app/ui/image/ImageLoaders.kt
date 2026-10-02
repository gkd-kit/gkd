package li.gkd.app.ui.image

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

object ImageLoaders {
    fun create(
        context: PlatformContext,
        cache: File,
        components: ComponentRegistry.Builder.() -> Unit = {}
    ) =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder().directory(cache.toOkioPath()).maxSizePercent(0.1).build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = {
                    OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
                        .build()
                }))
                components()
            }.build()
}
