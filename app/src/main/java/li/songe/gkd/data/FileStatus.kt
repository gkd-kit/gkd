package li.songe.gkd.data

sealed class FileStatus<T> {
    object NotFound : FileStatus<Nothing>()
    data class Failure(val e: Exception) : FileStatus<Nothing>()
    data class Ok<T>(val data: T) : FileStatus<T>()
}
