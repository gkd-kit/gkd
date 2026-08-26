package li.songe.gkd.util

sealed class LoadStatus<out T> {
    data class Loading(val progress: Float = 0f) : LoadStatus<Nothing>()
    data class Failure(val exception: Exception) : LoadStatus<Nothing>()
    data class Success<T>(val result: T) : LoadStatus<T>()
}
