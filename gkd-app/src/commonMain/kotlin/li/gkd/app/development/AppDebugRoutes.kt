package li.gkd.app.development

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.development.matching.matchDebugSnapshot
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.model.AppInfo
import li.gkd.app.settings.SettingsRepository

@Serializable
data class AppDebugState(
    val appId: String,
    val version: String,
    val enableAutomator: Boolean,
    val automatorMode: Int,
    val enableMatch: Boolean,
)

@Serializable
data class AppDebugCatalog(
    val loaded: Boolean,
    val refreshing: Boolean,
    val error: String?,
    val apps: List<AppInfo>,
)

/** Read-only projections of production repositories; no private settings are exported. */
fun Route.appDebugRoutes() {
    get("/debug/readiness") {
        val settings = SettingsRepository.settings.value
        val catalog = AppInfoRepository.state.value
        val session = MatchingRuntime.coordinator.diagnostics.state.value
        call.respond(
            buildAppDebugReadiness(
                AppDebugState(
                    META.appId,
                    META.versionName,
                    settings.enableAutomator,
                    settings.automatorMode,
                    settings.enableMatch,
                ),
                AppDebugCatalogStatus(
                    catalog.snapshot != null,
                    catalog.refreshing,
                    catalog.failure?.message,
                    catalog.snapshot?.apps?.size ?: 0,
                ),
                matchDebugSnapshot(),
                session?.let {
                    AppDebugSessionStatus(
                        it.id,
                        it.active,
                        !it.active &&
                            MatchingRuntime.coordinator.diagnostics.systemRecorder?.sealed == false,
                    )
                },
            ),
        )
    }
    get("/debug/app") {
        val settings = SettingsRepository.settings.value
        call.respond(
            AppDebugState(
                META.appId,
                META.versionName,
                settings.enableAutomator,
                settings.automatorMode,
                settings.enableMatch,
            ),
        )
    }
    get("/debug/app/catalog") {
        val state = AppInfoRepository.state.value
        call.respond(
            AppDebugCatalog(
                state.snapshot != null,
                state.refreshing,
                state.failure?.message,
                state.snapshot?.apps?.values?.toList().orEmpty(),
            ),
        )
    }
}
