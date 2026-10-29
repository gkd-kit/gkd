package li.gkd.app.matching.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchEventDecision(val wireValue: String) {
    @SerialName("secondary_display") SecondaryDisplay("secondary_display"),
    @SerialName("screen_off_content") ScreenOffContent("screen_off_content"),
    @SerialName("input_method_event") InputMethodEvent("input_method_event"),
    @SerialName("own_app_event") OwnAppEvent("own_app_event"),
    @SerialName("content_merged") ContentMerged("content_merged"),
    @SerialName("stale_accessibility_event") StaleAccessibilityEvent("stale_accessibility_event"),
    @SerialName("event_accepted") EventAccepted("event_accepted"),
}
