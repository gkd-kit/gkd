package li.gkd.app.ui.platform

import li.gkd.app.settings.AutomatorMode

expect fun UiHost.setStatusServiceEnabled(enabled: Boolean)
expect fun UiHost.changeAutomatorMode(mode: AutomatorMode)
