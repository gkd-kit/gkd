package li.gkd.app.ui.platform

import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService

actual fun UiHost.setStatusServiceEnabled(enabled: Boolean) {
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Status, enabled)
}
