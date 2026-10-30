package li.gkd.app.matching.input

/** Window associations observed during one accessibility connection, not Activity ownership IDs. */
class ForegroundWindowTracker {
    private val identities = mutableMapOf<Int, ForegroundIdentity>()
    private var windowId: Int? = null
    private var appId: String? = null

    val identity: ForegroundIdentity?
        get() = knownIdentity(windowId, appId)

    fun knownIdentity(id: Int?, app: String?): ForegroundIdentity? =
        identities[id]?.takeIf { it.appId == app }

    fun observeState(id: Int, value: ForegroundIdentity): Boolean {
        if (id < 0) return false
        val previous = identity
        identities[id] = value
        return select(id, value.appId) || previous != identity
    }

    fun observeRoot(id: Int, app: String, initialIdentity: ForegroundIdentity?): Boolean {
        if (id < 0) return false
        // A service may attach to an already open window without receiving a new window event.
        if (windowId == null) {
            identities[id] = initialIdentity?.takeIf { it.appId == app } ?: ForegroundIdentity(app)
        }
        return select(id, app)
    }

    private fun select(id: Int, app: String): Boolean {
        val changed = windowId != id || appId != app
        windowId = id
        appId = app
        return changed
    }

    fun isCurrentKnownWindow(id: Int, app: String?): Boolean =
        windowId == id && appId == app && identity != null
}
