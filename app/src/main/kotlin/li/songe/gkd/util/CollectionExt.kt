package li.songe.gkd.util

fun <T> Set<T>.switchItem(t: T): Set<T> {
    return if (contains(t)) {
        minus(t)
    } else {
        plus(t)
    }
}
