package li.gkd.app.storage

import java.io.IOException
import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage

class StorageException(
    val issue: FileOperationIssue,
    vararg val arguments: Any?,
    cause: Throwable? = null,
) : IOException(issue.name + arguments.joinToString(prefix = ": "), cause), BusinessError {
    override val errorCode: String get() = issue.name
    override val errorMessage: ErrorMessage
        get() = ErrorMessage.Resource(issue.messageResource, arguments.map { ErrorMessage.Text(it.toString()) })
}
