package li.gkd.app.model

@JvmInline
value class ScreenSize private constructor(private val packed: Long) {
    constructor(
        width: Int,
        height: Int,
    ) : this(
        (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL),
    )

    val width: Int
        get() = (packed shr 32).toInt()

    val height: Int
        get() = packed.toInt()
}
