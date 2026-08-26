package li.songe.gkd.util

fun <T> Set<T>.switchItem(t: T): Set<T> {
    return if (contains(t)) {
        minus(t)
    } else {
        plus(t)
    }
}

inline fun <T> Iterable<T>.filterIfNotAll(predicate: (T) -> Boolean): List<T> {
    return if (count() > 0 && !all(predicate)) {
        filter(predicate)
    } else {
        this as? List ?: toList()
    }
}

inline fun <T, K> Iterable<T>.distinctByIfAny(selector: (T) -> K): List<T> {
    return if (count() > 1 && any { v1 -> any { v2 -> v1 !== v2 && selector(v1) == selector(v2) } }) {
        distinctBy(selector)
    } else {
        this as? List ?: toList()
    }
}
