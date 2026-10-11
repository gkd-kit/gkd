package li.gkd.app.resources

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Blocking bridge for synchronous callbacks. Prefer stringResource in Compose and getString in coroutines.
 * Reads can perform I/O. Never call during static initialization or from high-frequency work.
 * The result is deliberately not cached here so a later call observes the current language.
 */
fun StringResource.getSync(vararg arguments: Any?): String = runBlocking {
    getString(this@getSync, *arguments.map { it.toString() }.toTypedArray())
}
