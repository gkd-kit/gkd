package li.songe.gkd.util

sealed class Status<out T> {
    object Empty : Status<Nothing>()

    /**
     * @param value 0f to 1f
     */
    class Progress(val value: Float = 0f) : Status<Nothing>()
    class Success<T>(val value: T) : Status<T>()
    class Error(val value: Any?) : Status<Nothing>() {
    }
}
