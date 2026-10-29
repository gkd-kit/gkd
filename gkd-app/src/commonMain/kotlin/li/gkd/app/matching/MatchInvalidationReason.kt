package li.gkd.app.matching

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchInvalidationReason(val wireValue: String) {
    @SerialName("automation_disabled") AutomationDisabled("automation_disabled"),
    @SerialName("screen_off") ScreenOff("screen_off"),
    @SerialName("foreground_conflict") ForegroundConflict("foreground_conflict"),
    @SerialName("page_changed") PageChanged("page_changed"),
    @SerialName("service_changed") ServiceChanged("service_changed"),
    @SerialName("service_disconnected") ServiceDisconnected("service_disconnected"),
    @SerialName("application_closed") ApplicationClosed("application_closed"),
}
