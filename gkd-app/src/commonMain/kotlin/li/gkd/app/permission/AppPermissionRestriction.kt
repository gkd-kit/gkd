package li.gkd.app.permission

import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

enum class AppPermissionRestriction(val descriptionResource: StringResource) {
    Accessibility(Res.string.restriction_app_accessibility),
    RestrictedSettings(Res.string.restriction_app_settings),
    ForegroundService(Res.string.restriction_app_foreground),
}
