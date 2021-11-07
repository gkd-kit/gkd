package li.songe.ad_closer.log

data class OperationRecord(
    val timestamp: Long,
    val packageName: String,
    val classNme: String,
    val ruleId: String)
