package li.gkd.app.network

/** A preflight hint, not a guarantee that the subsequent request will succeed. */
expect suspend fun isNetworkAvailable(): Boolean
