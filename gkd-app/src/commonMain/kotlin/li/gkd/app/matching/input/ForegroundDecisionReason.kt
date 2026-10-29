package li.gkd.app.matching.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ForegroundDecisionReason(val wireValue: String) {
    @SerialName("initial") Initial("initial"),
    @SerialName("surface_unchanged") SurfaceUnchanged("surface_unchanged"),
    @SerialName("surface_unavailable") SurfaceUnavailable("surface_unavailable"),
    @SerialName("system_surface_entered") SystemSurfaceEntered("system_surface_entered"),
    @SerialName("system_surface_exited") SystemSurfaceExited("system_surface_exited"),
    @SerialName("surface_observed_clear") SurfaceObservedClear("surface_observed_clear"),
    @SerialName("task_below_system_surface") TaskBelowSystemSurface("task_below_system_surface"),
    @SerialName("exiting_system_surface") ExitingSystemSurface("exiting_system_surface"),
    @SerialName("task_below_confirmed_surface")
    TaskBelowConfirmedSurface("task_below_confirmed_surface"),
    @SerialName("unchanged") Unchanged("unchanged"),
    @SerialName("awaiting_nodes") AwaitingNodes("awaiting_nodes"),
    @SerialName("app_reentered") AppReentered("app_reentered"),
    @SerialName("surface_confirmed") SurfaceConfirmed("surface_confirmed"),
    @SerialName("nodes_confirmed") NodesConfirmed("nodes_confirmed"),
    @SerialName("stale_observation_discarded")
    StaleObservationDiscarded("stale_observation_discarded"),
    @SerialName("foreground_conflict") ForegroundConflict("foreground_conflict"),
}
