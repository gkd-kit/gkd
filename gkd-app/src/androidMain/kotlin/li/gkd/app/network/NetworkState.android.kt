package li.gkd.app.network

// Real requests determine reachability; no unrelated host gates them.
actual fun isNetworkRequestAllowed(): Boolean = true
