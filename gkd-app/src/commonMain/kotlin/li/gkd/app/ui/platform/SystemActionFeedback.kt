package li.gkd.app.ui.platform

import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.openExternalUri
import li.gkd.app.resources.Res
import li.gkd.app.resources.intent_launch_failed_prefix
import li.gkd.app.resources.platform_action_unsupported
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync

object SystemActionFeedback {
    fun openExternal(uri: String, toast: (String) -> Unit) {
        try {
            if (openExternalUri(uri) == PlatformResult.Unsupported) toast(Res.string.platform_action_unsupported.getSync())
        } catch (e: Exception) {
            toast(Res.string.intent_launch_failed_prefix.getSync() + (e.displayMessage()))
        }
    }
}
