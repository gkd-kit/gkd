package li.gkd.app.network

actual fun isNetworkAvailable(): Boolean = NetworkAvailability.canResolveProbeHost()
