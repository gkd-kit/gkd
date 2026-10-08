package li.gkd.app.network

actual suspend fun isNetworkAvailable(): Boolean = canReachNetwork()
