package li.songe.gkd.composition

interface CanOnServiceConnected {
    fun onServiceConnected(f: () -> Unit):Boolean
}