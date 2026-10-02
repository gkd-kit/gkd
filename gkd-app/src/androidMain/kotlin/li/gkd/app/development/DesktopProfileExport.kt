package li.gkd.app.development

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.a11y.launcherAppIdFlow
import li.gkd.app.app
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicit adb development export. Release builds consume but never execute this action. */
object DesktopProfileExport {
    private val settingsJson = Json { encodeDefaults = true }
    private const val ACTION = "li.gkd.action.EXPORT_DESKTOP_PROFILE"
    private val mutex = Mutex()
    private val requestPattern = Regex("[a-zA-Z0-9-]{1,64}")

    fun handle(activity: MainActivity, intent: Intent): Boolean {
        if (intent.action != ACTION) return false
        if (!META.debuggable) return true
        val id = intent.getStringExtra("requestId")?.takeIf(requestPattern::matches) ?: return true
        activity.lifecycleScope.launch {
            mutex.withLock {
                val root =
                    File(checkNotNull(app.getExternalFilesDir(null)), "development/desktop-profile")
                val request = File(root, id)
                try {
                    withContext(Dispatchers.IO) {
                        check(root.mkdirs() || root.isDirectory)
                        // Retain requests for a day so another client can still pull its ZIP.
                        root.listFiles()?.filter {
                            it.isDirectory && System.currentTimeMillis() - it.lastModified() > 86_400_000
                        }?.forEach { it.deleteRecursively() }
                    }
                    if (request.exists()) return@withLock
                    withContext(Dispatchers.IO) {
                        check(request.mkdirs())
                        status(request, "running")
                    }
                    withTimeout(120_000) {
                        while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                            activity.window.decorView.rootWindowInsets == null || activity.window.decorView.height == 0
                        ) delay(50)
                        val profile = captureWindow(activity)
                        withContext(Dispatchers.IO) { export(request, profile) }
                    }
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        File(request, "profile.zip.part").delete()
                        runCatching { status(request, "failed", e.stackTraceToString()) }
                            .onFailure {
                                LogUtils.d(
                                    "Cannot report desktop profile export failure",
                                    e,
                                    it
                                )
                            }
                    }
                    if (e is CancellationException) throw e
                }
            }
        }
        return true
    }

    private fun status(request: File, state: String, error: String? = null) {
        val temporary = File(request, "status.json.part")
        temporary.writeText(JSONObject().apply {
            put("requestId", request.name)
            put("state", state)
            if (error != null) put("error", error)
        }.toString())
        check(temporary.renameTo(File(request, "status.json")))
    }

    private fun captureWindow(activity: MainActivity): JSONObject {
        val context = activity
        val resources = activity.resources
        val density = resources.displayMetrics.density
        val insets =
            WindowInsetsCompat.toWindowInsetsCompat(checkNotNull(activity.window.decorView.rootWindowInsets))
        val statusInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val navigationInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        fun palette(scheme: ColorScheme) = JSONObject(
            mapOf(
                "primary" to scheme.primary.toArgb(),
                "onPrimary" to scheme.onPrimary.toArgb(),
                "primaryContainer" to scheme.primaryContainer.toArgb(),
                "onPrimaryContainer" to scheme.onPrimaryContainer.toArgb(),
                "inversePrimary" to scheme.inversePrimary.toArgb(),
                "secondary" to scheme.secondary.toArgb(),
                "onSecondary" to scheme.onSecondary.toArgb(),
                "secondaryContainer" to scheme.secondaryContainer.toArgb(),
                "onSecondaryContainer" to scheme.onSecondaryContainer.toArgb(),
                "tertiary" to scheme.tertiary.toArgb(),
                "onTertiary" to scheme.onTertiary.toArgb(),
                "tertiaryContainer" to scheme.tertiaryContainer.toArgb(),
                "onTertiaryContainer" to scheme.onTertiaryContainer.toArgb(),
                "background" to scheme.background.toArgb(),
                "onBackground" to scheme.onBackground.toArgb(),
                "surface" to scheme.surface.toArgb(),
                "onSurface" to scheme.onSurface.toArgb(),
                "surfaceVariant" to scheme.surfaceVariant.toArgb(),
                "onSurfaceVariant" to scheme.onSurfaceVariant.toArgb(),
                "surfaceTint" to scheme.surfaceTint.toArgb(),
                "inverseSurface" to scheme.inverseSurface.toArgb(),
                "inverseOnSurface" to scheme.inverseOnSurface.toArgb(),
                "error" to scheme.error.toArgb(),
                "onError" to scheme.onError.toArgb(),
                "errorContainer" to scheme.errorContainer.toArgb(),
                "onErrorContainer" to scheme.onErrorContainer.toArgb(),
                "outline" to scheme.outline.toArgb(),
                "outlineVariant" to scheme.outlineVariant.toArgb(),
                "scrim" to scheme.scrim.toArgb(),
                "surfaceBright" to scheme.surfaceBright.toArgb(),
                "surfaceDim" to scheme.surfaceDim.toArgb(),
                "surfaceContainer" to scheme.surfaceContainer.toArgb(),
                "surfaceContainerHigh" to scheme.surfaceContainerHigh.toArgb(),
                "surfaceContainerHighest" to scheme.surfaceContainerHighest.toArgb(),
                "surfaceContainerLow" to scheme.surfaceContainerLow.toArgb(),
                "surfaceContainerLowest" to scheme.surfaceContainerLowest.toArgb()
            )
        )

        val profile = JSONObject().apply {
            put("width", (activity.window.decorView.width / density).toInt())
            put("height", (activity.window.decorView.height / density).toInt())
            put("fontScale", resources.configuration.fontScale)
            put("density", density)
            put("statusBar", statusInset / density)
            put("navigationBar", navigationInset / density)
            put("appName", META.appName)
            put("versionName", META.versionName)
            put("versionCode", META.versionCode)
            put("launcherAppId", launcherAppIdFlow.value)
            put(
                "dark",
                SettingsRepository.settings.value.enableDarkTheme
                    ?: (resources.configuration.uiMode and 0x30 == 0x20)
            )
            put(
                "lightColors",
                palette(
                    if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else lightColorScheme()
                )
            )
            put(
                "darkColors",
                palette(
                    if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
                )
            )
        }
        return profile
    }

    private suspend fun export(request: File, profile: JSONObject) {
        val snapshot = SubscriptionRepository.snapshotFlow.first { it.value != null }.value!!
        check(snapshot.loadErrors.isEmpty()) { "Cannot export subscriptions: ${snapshot.loadErrors}" }
        val subscriptions = snapshot.subscriptions
        val catalog = AppInfoRepository.snapshots.first()
        val items = Db.subsItemDao.queryAll()
        val settings = JSONObject(settingsJson.encodeToString(SettingsRepository.settings.value))
        val settingKeys = listOf(
            "enableDarkTheme", "enableDynamicColor", "toastWhenClick", "useSystemToast",
            "actionToast", "excludeFromRecents", "enableBlockA11yAppList", "useCustomNotifText",
            "customNotifTitle", "customNotifText", "enableMatch", "enableStatusService", "appSort",
            "appGroupType", "showBlockApp"
        )
        val selectedSettings = JSONObject().apply {
            settingKeys.forEach { key -> if (settings.has(key)) put(key, settings.get(key)) }
        }
        val seed = JSONObject().apply {
            put("blocked", JSONArray(SettingsRepository.blockMatchAppList.value.toList()))
            put("subscriptions", JSONObject(items.associate { it.id.toString() to it.enable }))
        }
        val temporary = File(request, "profile.zip.part")
        ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
            fun text(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            text("profile.json", profile.toString())
            text("apps.json", Json.encodeToString(catalog.inventory))
            text("settings.json", selectedSettings.toString())
            text("seed.json", seed.toString())
            subscriptions.forEach { (id, value) ->
                text("subscriptions/$id.json", Json.encodeToString(value))
            }
            for (info in app.packageManager.getInstalledApplications(0)) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val bitmap = info.loadIcon(app.packageManager).toBitmap(96, 96)
                zip.putNextEntry(ZipEntry("icons/${info.packageName}.png"))
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
                zip.closeEntry()
            }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(temporary.renameTo(File(request, "profile.zip")))
        status(request, "success")
    }
}
