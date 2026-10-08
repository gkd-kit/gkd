package li.gkd.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import java.util.UUID

/** One visit to a route, including while its exit transition is still running. */
class AppNavEntry(val route: AppRoute) : NavKey {
    val id: String = UUID.randomUUID().toString()

    override fun toString(): String = route.toString()
}
