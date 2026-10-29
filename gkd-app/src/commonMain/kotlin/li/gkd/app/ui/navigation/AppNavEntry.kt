package li.gkd.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AdvancedSection(val linkValue: String) {
    SnapshotButton("snapshot-button"),
    SnapshotSettings("snapshot-settings"),
    Http("http"),
    Activity("activity"),
    Event("event"),
    Track("track"),
    Diagnostics("diagnostics");

    val uri: String
        get() = "gkd://page/1?section=$linkValue"
}

/** One visit to a route, including while its exit transition is still running. */
class AppNavEntry(val route: AppRoute) : NavKey {
    val id: String = UUID.randomUUID().toString()

    val advancedSectionRequest: StateFlow<AdvancedSection?>
        field = MutableStateFlow(null)

    fun requestAdvancedSection(section: AdvancedSection) {
        advancedSectionRequest.value = section
    }

    fun consumeAdvancedSection() {
        advancedSectionRequest.value = null
    }

    override fun toString(): String = route.toString()
}
