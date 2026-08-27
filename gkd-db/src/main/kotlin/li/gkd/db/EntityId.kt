package li.gkd.db

private var lastEntityId = 0L

@Synchronized
internal fun buildUniqueTimeMillisId(): Long {
    val currentTimeMillis = System.currentTimeMillis()
    if (currentTimeMillis > lastEntityId) {
        lastEntityId = currentTimeMillis
    } else {
        lastEntityId += 1
    }
    return lastEntityId
}
