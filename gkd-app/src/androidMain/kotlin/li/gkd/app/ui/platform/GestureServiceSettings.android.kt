package li.gkd.app.ui.platform

import android.content.Intent
import android.provider.Settings
import li.gkd.app.platform.PlatformResult

actual fun UiHost.openGestureServiceSettings(): PlatformResult<Unit> {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    return PlatformResult.Success(Unit)
}
