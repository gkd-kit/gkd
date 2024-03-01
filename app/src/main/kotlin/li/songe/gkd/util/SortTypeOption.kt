package li.songe.gkd.util

sealed class SortTypeOption(val value: Int, val label: String) {
    data object SortByName : SortTypeOption(0, "按名称")
    data object SortByAppMtime : SortTypeOption(1, "按更新时间")
    data object SortByTriggerTime : SortTypeOption(2, "按触发时间")

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubObject by lazy { arrayOf(SortByName, SortByAppMtime, SortByTriggerTime) }
    }
}