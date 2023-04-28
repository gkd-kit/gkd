package li.songe.selector.selector

import li.songe.selector.expression.BinaryExpression


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
