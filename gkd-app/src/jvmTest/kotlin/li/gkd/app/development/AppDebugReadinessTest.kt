package li.gkd.app.development

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.matching.MatchEngineSnapshot
import li.gkd.app.matching.diagnostics.ForegroundDebugState
import li.gkd.app.matching.diagnostics.MatchDebugSnapshot
import li.gkd.app.matching.input.ForegroundDecision
import li.gkd.app.matching.input.ForegroundDecisionReason
import li.gkd.app.matching.input.ForegroundIdentity
import li.gkd.app.matching.input.MatchTimingPolicy

class AppDebugReadinessTest {
    private val app = AppDebugState("test", "1", true, 1, false)
    private val catalog = AppDebugCatalogStatus(true, false, null, 1)
    private val surface =
        ForegroundDebugState(
            true,
            false,
            1,
            ForegroundDecision(
                ForegroundIdentity("com.android.systemui"),
                null,
                ForegroundDecisionReason.SurfaceConfirmed,
                false,
                task = ForegroundIdentity("underlying.app", "Main"),
                nodeAppId = "com.android.systemui",
            ),
            true,
        )
    private val runtime =
        MatchDebugSnapshot(
            MatchEngineSnapshot(true, 1, null, false, null, 0, MatchTimingPolicy()),
            surface,
        )

    @Test
    fun overlayAndLockScreenCanBeQueriedButLockedBenchmarkCannotRun() {
        // Different task/node packages and a missing Activity are valid for notification/keyguard
        // surfaces.
        val overlay = buildAppDebugReadiness(app, catalog, runtime, null)
        assertTrue(overlay.checks.getValue("liveQuery").ready)
        assertTrue(overlay.checks.getValue("androidBenchmark").ready)
        val locked =
            buildAppDebugReadiness(
                app,
                catalog,
                runtime.copy(foreground = surface.copy(locked = true)),
                null,
            )
        assertTrue(locked.checks.getValue("liveQuery").ready)
        assertEquals(listOf("screen_locked"), locked.checks.getValue("androidBenchmark").reasons)
    }

    @Test
    fun observationAndCaptureRemainAvailableWhileServiceOrCatalogIsNotReady() {
        val waiting =
            buildAppDebugReadiness(
                app,
                catalog.copy(loaded = false, error = "query failed"),
                runtime.copy(foreground = surface.copy(serviceEffective = false)),
                null,
            )
        assertTrue(waiting.checks.getValue("observe").ready)
        assertTrue(waiting.checks.getValue("startCapture").ready)
        assertEquals(
            listOf("service_not_effective", "catalog_load_failed"),
            waiting.checks.getValue("liveQuery").reasons,
        )
        // A refresh failure with an existing catalog remains usable; retain the error as evidence.
        val stale =
            buildAppDebugReadiness(
                app,
                catalog.copy(refreshing = true, error = "refresh failed"),
                runtime,
                null,
            )
        assertTrue(stale.checks.getValue("liveQuery").ready)
        assertEquals("refresh failed", stale.catalog.error)
    }

    @Test
    fun disconnectedServiceWithPendingCleanupRemainsObservableAndCannotRunAnotherInput() {
        val pending =
            runtime.copy(
                engine = runtime.engine.copy(attached = false, activeRunId = 42, cancelling = true),
                foreground = surface.copy(serviceEffective = false),
            )
        val state =
            buildAppDebugReadiness(
                app,
                catalog,
                pending,
                AppDebugSessionStatus("capture", false, true),
            )
        assertTrue(state.checks.getValue("observe").ready)
        assertFalse(state.checks.getValue("startCapture").ready)
        assertEquals(
            listOf(
                "service_not_effective",
                "engine_not_attached",
                "engine_busy",
                "platform_cleanup_pending",
                "service_disconnected_cleanup_unconfirmed",
            ),
            state.checks.getValue("liveQuery").reasons,
        )
    }

    @Test
    fun busyInputDoesNotPreventObservationAndExistingSessionIsDiscoverable() {
        val session = AppDebugSessionStatus("existing-user-session", true)
        val busy =
            buildAppDebugReadiness(
                app.copy(enableMatch = true),
                catalog,
                runtime.copy(engine = runtime.engine.copy(activeRunId = 42)),
                session,
            )
        assertTrue(busy.checks.getValue("observe").ready)
        assertFalse(busy.checks.getValue("startCapture").ready)
        assertEquals("existing-user-session", busy.session?.id)
        assertEquals(listOf("engine_busy"), busy.checks.getValue("liveQuery").reasons)
        assertEquals(
            listOf("engine_busy", "rule_matching_enabled", "diagnostic_session_active"),
            busy.checks.getValue("androidBenchmark").reasons,
        )
        val desktop = buildAppDebugReadiness(app, catalog, runtime.copy(foreground = null), null)
        assertEquals(listOf("live_nodes_unsupported"), desktop.checks.getValue("liveQuery").reasons)
        assertTrue(desktop.checks.getValue("startCapture").ready)
    }
}
