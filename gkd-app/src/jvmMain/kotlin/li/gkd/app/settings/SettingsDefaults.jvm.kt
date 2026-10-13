package li.gkd.app.settings

import li.gkd.app.META
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import li.gkd.app.DesktopProfile

private val settingsJson = Json { ignoreUnknownKeys = true }

actual fun defaultSettings(): SettingsStore {
    return settingsJson.decodeFromJsonElement(
        JsonObject(
            mapOf(
                "actionToast" to JsonPrimitive(META.appName),
                "customNotifTitle" to JsonPrimitive(META.appName),
            ) + DesktopProfile.settings
        )
    )
}

actual fun defaultBlockMatchAppList(): Set<String> = DesktopProfile.blocked

actual fun defaultTermsAccepted(): Boolean = true
