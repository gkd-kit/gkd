package li.gkd.app.ui.platform

import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.openExternalUri
import li.gkd.app.resources.*
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils

object SystemActionFeedback {
    fun openExternal(uri: String) {
        try {
            if (openExternalUri(uri) == PlatformResult.Unsupported) ToastUtils.show(Res.string.platform_action_unsupported.getSync())
        } catch (e: Exception) {
            ToastUtils.show(Res.string.intent_launch_failed_prefix.getSync() + (e.displayMessage()))
        }
    }
}
