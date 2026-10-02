package li.gkd.app.ui.share

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.MainActivity

fun Modifier.optimizedImePadding() = composed {
    val activity = LocalActivity.current as MainActivity
    if (activity.imeController.showAnimationRunningFlow.collectAsStateWithLifecycle().value) {
        this
    } else {
        imePadding()
    }
}
