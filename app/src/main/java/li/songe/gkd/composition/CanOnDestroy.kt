package li.songe.gkd.composition

interface CanOnDestroy {
    fun onDestroy(f: () -> Unit): Boolean
}