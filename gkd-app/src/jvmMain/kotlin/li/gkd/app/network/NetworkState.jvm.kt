package li.gkd.app.network

import li.gkd.app.DesktopRuntime

actual fun isNetworkAvailable(): Boolean =
    DesktopRuntime.requireCurrent().simulator.settings.value.networkAvailable
