package li.gkd.app.network

import li.gkd.app.DesktopRuntime

actual suspend fun isNetworkAvailable(): Boolean =
    DesktopRuntime.requireCurrent().simulator.settings.value.networkAvailable
