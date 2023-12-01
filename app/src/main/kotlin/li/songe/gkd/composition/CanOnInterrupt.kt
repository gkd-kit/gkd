package li.songe.gkd.composition

interface CanOnInterrupt {
    fun onInterrupt(f: () -> Unit):Boolean
}