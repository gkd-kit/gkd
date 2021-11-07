package li.songe.ad_closer.data


data class RuleGroup(
    val id: Long,
    val description: String,
    val packageName: String,
    val className: String,
    val ruleList: List<String>
)
// 从网址导入时, 会显示 规则描述 目标应用 目标活动界面, 此界面可点击打开
