package li.songe.gkd.data

import androidx.compose.runtime.Immutable

@Immutable
data class Tuple3<T0, T1, T2>(
    val t0: T0,
    val t1: T1,
    val t2: T2,
) {
    override fun toString() = "($t0, $t1, $t2)"
}
