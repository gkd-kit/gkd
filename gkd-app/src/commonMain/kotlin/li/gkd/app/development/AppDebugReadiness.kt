package li.gkd.app.development

import kotlinx.serialization.Serializable
import li.gkd.app.matching.diagnostics.MatchDebugSnapshot

@Serializable
data class AppDebugCatalogStatus(
    val loaded: Boolean,
    val refreshing: Boolean,
    val error: String?,
    val count: Int,
)

@Serializable
data class AppDebugSessionStatus(
    val id: String,
    val active: Boolean,
    val systemFinalizing: Boolean = false,
)

@Serializable data class DebugReadinessCheck(val ready: Boolean, val reasons: List<String>)

@Serializable
data class AppDebugReadiness(
    val app: AppDebugState,
    val catalog: AppDebugCatalogStatus,
    val runtime: MatchDebugSnapshot,
    val session: AppDebugSessionStatus?,
    val checks: Map<String, DebugReadinessCheck>,
)

/** Advisory observations only: no node reads, permission requests or changes to running work. */
fun buildAppDebugReadiness(
    app: AppDebugState,
    catalog: AppDebugCatalogStatus,
    runtime: MatchDebugSnapshot,
    session: AppDebugSessionStatus?,
): AppDebugReadiness {
    val foreground = runtime.foreground
    val liveReasons = buildList {
        if (foreground == null) add("live_nodes_unsupported")
        else {
            if (!foreground.serviceEffective) add("service_not_effective")
            if (!foreground.interactive) add("screen_not_interactive")
            if (foreground.foreground.pending != null) add("foreground_pending")
            if (foreground.foreground.confirmed.appId.isBlank()) add("foreground_unknown")
            if (!runtime.engine.attached) add("engine_not_attached")
            if (runtime.engine.activeRunId != null) add("engine_busy")
            if (runtime.engine.cancelling) {
                add("platform_cleanup_pending")
                if (!runtime.engine.attached) add("service_disconnected_cleanup_unconfirmed")
            }
            if (!catalog.loaded)
                add(if (catalog.error == null) "catalog_not_loaded" else "catalog_load_failed")
        }
    }
    fun check(reasons: List<String>) = DebugReadinessCheck(reasons.isEmpty(), reasons)
    return AppDebugReadiness(
        app,
        catalog,
        runtime,
        session,
        mapOf(
            "observe" to check(emptyList()),
            "startCapture" to
                check(
                    buildList {
                        if (session?.active == true) add("diagnostic_session_active")
                        if (session?.systemFinalizing == true) add("system_recording_finalizing")
                    },
                ),
            "liveQuery" to check(liveReasons),
            "androidBenchmark" to
                check(
                    liveReasons +
                        buildList {
                            if (foreground != null) {
                                if (foreground.locked) add("screen_locked")
                                if (app.enableMatch) add("rule_matching_enabled")
                                if (session?.active == true) add("diagnostic_session_active")
                            }
                        },
                ),
        ),
    )
}
