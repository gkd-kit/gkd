package li.gkd.gradle

import com.android.build.api.dsl.ApkSigningConfig
import java.io.File
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

// Keep the result independent of AGP's runtime classloader as well.
class GkdSigningConfigs<T>(val gkd: T, val play: T)

// Inline keeps AGP type access in the caller's plugin classloader.
@Suppress("NOTHING_TO_INLINE")
inline fun Project.getOrCreateSigningConfigs(
    configs: NamedDomainObjectContainer<out ApkSigningConfig>,
): GkdSigningConfigs<ApkSigningConfig> {
    // Preserve each signing property's existing relative-path base.
    val gkd =
        resolveSigningConfig(
            configs,
            "gkd",
            "GKD",
            rootProject.file("gkd-android"),
            configs.getByName("debug"),
        )
    val play = resolveSigningConfig(configs, "play", "PLAY", projectDir, gkd)
    return GkdSigningConfigs(gkd, play)
}

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun Project.resolveSigningConfig(
    configs: NamedDomainObjectContainer<out ApkSigningConfig>,
    name: String,
    prefix: String,
    baseDir: File,
    fallback: ApkSigningConfig,
): ApkSigningConfig {
    configs.findByName(name)?.let {
        return it
    }
    val path = buildProperty("${prefix}_STORE_FILE").orNull ?: return fallback
    return configs.create(name).apply {
        storeFile = baseDir.resolve(path)
        storePassword = buildProperty("${prefix}_STORE_PASSWORD").orNull
        keyAlias = buildProperty("${prefix}_KEY_ALIAS").orNull
        keyPassword = buildProperty("${prefix}_KEY_PASSWORD").orNull
    }
}
