package li.gkd.app.ui.platform

import li.gkd.app.ui.option.AutomatorModeOption

expect fun UiHost.setStatusServiceEnabled(enabled: Boolean)
expect fun UiHost.changeAutomatorMode(mode: AutomatorModeOption)
