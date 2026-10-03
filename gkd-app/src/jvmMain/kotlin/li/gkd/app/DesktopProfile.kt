package li.gkd.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.model.WebViewEnvironment
import li.gkd.app.network.AppLinks
import org.jetbrains.skia.Image

/** Local development imports are intentionally kept outside source control. */
object DesktopProfile {
    val directory get() = DesktopStorage.profile
    private val json = Json { ignoreUnknownKeys = true }
    private fun read(name: String) = directory.resolve(name).takeIf { it.isFile }?.readText()
        ?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

    private val profile = read("profile.json")
    val settings = read("settings.json")
    private val seed = read("seed.json")
    val blocked = seed["blocked"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
    val subscriptionEnabled = seed["subscriptions"]?.jsonObject
        ?.mapKeys { it.key.toLong() }?.mapValues { it.value.jsonPrimitive.boolean }.orEmpty()

    val launcherAppId = profile["launcherAppId"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val channel = profile["channel"]?.jsonPrimitive?.content ?: "desktop"
    val appId = profile["appId"]?.jsonPrimitive?.content ?: "li.songe.gkd"
    val debuggable = true
    val versionCode = profile["versionCode"]?.jsonPrimitive?.content ?: "—"
    val commitLabel = profile["commitId"]?.jsonPrimitive?.content?.take(16) ?: "—"
    val commitTime = profile["commitTimeText"]?.jsonPrimitive?.content ?: "—"
    val commitUrl = profile["commitUrl"]?.jsonPrimitive?.content ?: AppLinks.Repository
    val versionName = profile["versionName"]?.jsonPrimitive?.content ?: "Desktop 开发版本"
    val appName = profile["appName"]?.jsonPrimitive?.content ?: "GKD"
    val webViewEnvironmentJson by lazy {
        WebViewEnvironment(
            platform = "desktop",
            appId = appId,
            appName = appName,
            versionCode = versionCode.toIntOrNull() ?: 0,
            versionName = versionName,
            channel = channel,
            debuggable = debuggable,
        ).toJson()
    }
    val statusBar = profile["statusBar"]?.jsonPrimitive?.float ?: defaultSimulatedDevice.statusBarHeight
    val navigationBar = profile["navigationBar"]?.jsonPrimitive?.float ?: defaultSimulatedDevice.navigationBarHeight
    val environment = DesktopEnvironment(
        width = profile["width"]?.jsonPrimitive?.int ?: defaultSimulatedDevice.width,
        height = profile["height"]?.jsonPrimitive?.int ?: defaultSimulatedDevice.height,
        fontScale = profile["fontScale"]?.jsonPrimitive?.float ?: defaultSimulatedDevice.fontScale,
        dark = profile["dark"]?.jsonPrimitive?.boolean ?: false,
        android = AndroidWindow(statusBarHeight = statusBar, navigationBarHeight = navigationBar),
    )

    fun readIcon(id: String): BitmapPainter? =
        directory.resolve("icons/$id.png").takeIf { it.isFile }?.let {
            BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
        }

    fun colors(dark: Boolean, dynamic: Boolean): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val values =
            if (dynamic) profile[if (dark) "darkColors" else "lightColors"]?.jsonObject else null

        fun color(key: String, fallback: Color) =
            values?.get(key)?.jsonPrimitive?.int?.let(::Color) ?: fallback
        return base.copy(
            primary = color("primary", base.primary),
            onPrimary = color("onPrimary", base.onPrimary),
            primaryContainer = color("primaryContainer", base.primaryContainer),
            onPrimaryContainer = color("onPrimaryContainer", base.onPrimaryContainer),
            inversePrimary = color("inversePrimary", base.inversePrimary),
            secondary = color("secondary", base.secondary),
            onSecondary = color("onSecondary", base.onSecondary),
            secondaryContainer = color("secondaryContainer", base.secondaryContainer),
            onSecondaryContainer = color("onSecondaryContainer", base.onSecondaryContainer),
            tertiary = color("tertiary", base.tertiary),
            onTertiary = color("onTertiary", base.onTertiary),
            tertiaryContainer = color("tertiaryContainer", base.tertiaryContainer),
            onTertiaryContainer = color("onTertiaryContainer", base.onTertiaryContainer),
            background = color("background", base.background),
            onBackground = color("onBackground", base.onBackground),
            surface = color("surface", base.surface),
            onSurface = color("onSurface", base.onSurface),
            surfaceVariant = color("surfaceVariant", base.surfaceVariant),
            onSurfaceVariant = color("onSurfaceVariant", base.onSurfaceVariant),
            surfaceTint = color("surfaceTint", base.surfaceTint),
            inverseSurface = color("inverseSurface", base.inverseSurface),
            inverseOnSurface = color("inverseOnSurface", base.inverseOnSurface),
            error = color("error", base.error),
            onError = color("onError", base.onError),
            errorContainer = color("errorContainer", base.errorContainer),
            onErrorContainer = color("onErrorContainer", base.onErrorContainer),
            outline = color("outline", base.outline),
            outlineVariant = color("outlineVariant", base.outlineVariant),
            scrim = color("scrim", base.scrim),
            surfaceBright = color("surfaceBright", base.surfaceBright),
            surfaceDim = color("surfaceDim", base.surfaceDim),
            surfaceContainer = color("surfaceContainer", base.surfaceContainer),
            surfaceContainerHigh = color("surfaceContainerHigh", base.surfaceContainerHigh),
            surfaceContainerHighest = color(
                "surfaceContainerHighest",
                base.surfaceContainerHighest
            ),
            surfaceContainerLow = color("surfaceContainerLow", base.surfaceContainerLow),
            surfaceContainerLowest = color("surfaceContainerLowest", base.surfaceContainerLowest)
        )
    }

    val typography: Typography by lazy {
        val base = Typography()
        val file = directory.resolve("DeviceSans.ttf").takeIf { it.isFile }
            ?: directory.resolve("MiSansVF.ttf")
        val family = if (file.isFile) FontFamily(
            Font(file, weight = FontWeight.Normal),
            Font(file, weight = FontWeight.Medium),
            Font(file, weight = FontWeight.Bold),
        ) else FontFamily.Default
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family)
        )
    }

    fun boolean(key: String, fallback: Boolean) =
        settings[key]?.jsonPrimitive?.booleanOrNull ?: fallback

    fun text(key: String, fallback: String) =
        settings[key]?.jsonPrimitive?.contentOrNull ?: fallback
}
