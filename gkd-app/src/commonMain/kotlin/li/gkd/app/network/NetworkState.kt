package li.gkd.app.network

/** Allows the development host to simulate being offline; real hosts attempt the request. */
expect fun isNetworkRequestAllowed(): Boolean
