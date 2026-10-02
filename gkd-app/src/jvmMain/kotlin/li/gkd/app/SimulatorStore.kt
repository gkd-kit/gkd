package li.gkd.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.Locale

/** Commands validate complete snapshots before handing them to the host's persistence boundary. */
class SimulatorStore(
    initial: SimulatorSettings = SimulatorSettings().withEnvironment(DesktopEnvironment()),
    private val save: (SimulatorSettings) -> Unit = {},
) {
    init {
        initial.validate(); Locale.setDefault(Locale.forLanguageTag(initial.environment().locale))
    }

    var onAppCatalogChanged: (() -> Unit)? = null

    private val json = Json { encodeDefaults = true }
    private var hostBatteryPercent: Int? = null

    val settings: StateFlow<SimulatorSettings>
        field = MutableStateFlow(initial)

    @Synchronized
    fun update(transform: (SimulatorSettings) -> SimulatorSettings) {
        val previous = settings.value
        val transformed = transform(previous)
        val next = hostBatteryPercent?.let {
            transformed.copy(
                device = transformed.device.copy(batteryPercent = it)
            )
        } ?: transformed
        next.validate()
        if (next == previous) return
        val persistent = next.persistent()
        if (persistent != previous.persistent()) save(persistent)
        if (next.environment().locale != previous.environment().locale) {
            Locale.setDefault(Locale.forLanguageTag(next.environment().locale))
        }
        settings.value = next
        if (previous.permissions.canQueryPackages != next.permissions.canQueryPackages ||
            previous.permissions.queryPackagesAbnormal != next.permissions.queryPackagesAbnormal
        ) onAppCatalogChanged?.invoke()
    }

    fun configure(value: DesktopEnvironment) = update { it.withEnvironment(value) }

    @Synchronized
    fun updateHostBattery(percent: Int) {
        hostBatteryPercent = percent.takeIf { it in 0..100 } ?: 100
        update { it }
    }

    fun replace(value: SimulatorSettings) = update { current ->
        value.persistent().copy(
            privilege = value.privilege.persistent()
                .copy(operationId = current.privilege.operationId + 1)
        )
    }

    /** Field updates use the same model as the file and StateFlow. */
    fun patch(patch: JsonObject) = update { current ->
        val previous = json.encodeToJsonElement(current).jsonObject
        val next = json.decodeFromJsonElement<SimulatorSettings>(merge(previous, patch))
        next.copy(
            privilege = if ("privilege" in patch)
                next.privilege.cancelled().copy(operationId = current.privilege.operationId + 1)
            else current.privilege
        )
    }

    private fun merge(current: JsonObject, patch: JsonObject): JsonObject {
        val next = current.toMutableMap()
        patch.forEach { (key, value) ->
            require(key in current) { "Unknown simulator field: $key" }
            val original = current.getValue(key)
            next[key] =
                if (original is JsonObject && value is JsonObject) merge(original, value) else value
        }
        return JsonObject(next)
    }

    fun updateEnvironment(transform: (DesktopEnvironment) -> DesktopEnvironment) =
        update { it.withEnvironment(normalizeEnvironment(transform(it.environment()))) }

    private fun normalizeEnvironment(value: DesktopEnvironment): DesktopEnvironment {
        val maximumImeHeight =
            (value.height - value.android.topInset - value.android.bottomInset - 1f).coerceAtLeast(
                100f
            )
        return value.copy(
            android = value.android.copy(
                imeHeight = value.android.imeHeight.coerceAtMost(
                    maximumImeHeight
                )
            )
        )
    }

    fun updateAndroid(transform: (AndroidWindow) -> AndroidWindow) =
        updateEnvironment { it.copy(android = transform(it.android)) }

    fun setRestricted(value: Boolean) =
        update { it.copy(permissions = it.permissions.copy(restricted = value)) }

    fun setWifi(value: Boolean) = update { it.copy(device = it.device.copy(wifi = value)) }
    fun setPrivilegeAvailable(value: Boolean) =
        update { it.copy(privilege = it.privilege.withAvailability(value)) }

    fun grantBatteryExemption() =
        update { it.copy(permissions = it.permissions.copy(ignoreBatteryOptimizations = true)) }

    fun grantLocalNetwork() =
        update { it.copy(permissions = it.permissions.copy(localNetworkGranted = true)) }

    fun updatePrivilege(
        operationId: Long? = null,
        transform: (SimulatedPrivilege) -> SimulatedPrivilege
    ) = update {
        if (operationId != null && operationId != it.privilege.operationId) it
        else it.copy(privilege = transform(it.privilege))
    }
}
