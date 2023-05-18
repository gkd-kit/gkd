package li.songe.selector_android.selector

import li.songe.selector_android.expression.BinaryExpression


data class PropertySelector(
    /**
     * 此属性选择器是否被 @ 标记
     */
    val match: Boolean,
    val name: String,
    val expressionList: List<BinaryExpression>,
) {
    override fun toString() = "${if (match) "@" else ""}${name}${expressionList.joinToString("")}"

    val needMatchName = name != "*" && name.isNotEmpty()
}
