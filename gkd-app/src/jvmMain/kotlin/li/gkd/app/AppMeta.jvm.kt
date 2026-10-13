package li.gkd.app

import java.util.Properties

actual val META: AppMeta by lazy {
    val properties = Properties().apply {
        AppMeta::class.java.getResourceAsStream("/app-meta.properties")!!.use { load(it) }
    }
    AppMeta(
        channel = "desktop",
        buildKey = "",
        commitId = properties.getProperty("commitId"),
        commitTime = properties.getProperty("commitTime").toLong(),
        tagName = properties.getProperty("tagName").takeIf { it.isNotEmpty() },
        debuggable = true,
        versionCode = properties.getProperty("versionCode").toInt(),
        versionName = properties.getProperty("versionName"),
        appId = "li.songe.gkd.desktop",
        appName = "GKD",
    )
}
