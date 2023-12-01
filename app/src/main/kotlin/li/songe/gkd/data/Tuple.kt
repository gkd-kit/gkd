package li.songe.gkd.data

data class Tuple3<T0, T1, T2>(
    val t0: T0,
    val t1: T1,
    val t2: T2,
) {
    override fun toString() = "($t0, $t1, $t2)"
}

data class Tuple4<T0, T1, T2, T3>(
    val t0: T0,
    val t1: T1,
    val t2: T2,
    val t3: T3,
) {
    override fun toString() = "($t0, $t1, $t2, $t3)"
}

