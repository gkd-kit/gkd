package li.gkd.app.storage

import java.io.IOException

class StorageException(
    val issue: FileOperationIssue,
    vararg val arguments: Any?,
    cause: Throwable? = null,
) : IOException(issue.name + arguments.joinToString(prefix = ": "), cause)
