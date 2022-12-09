package li.songe.selector.selector

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.getDepth
import li.songe.selector.getIndex

/**
 * 属性选择器
 */
data class PropertySelector(
    val match: Boolean,
    val name: String,
    val expressionList: List<BinaryExpression>
) {
    override fun toString() = "${if (match) "@" else ""}${name}${expressionList.joinToString("")}"
    sealed class Operator(private val key: String) {

        object More : Operator(">")

        object Less : Operator("<")

        object Equal : Operator("=")

        object NotEqual : Operator("!=")

        object MoreEqual : Operator(">=")

        object LessEqual : Operator("<=")

        object Include : Operator("*=")

        object Start : Operator("^=")

        object End : Operator("$=")

        override fun toString() = key
    }

    data class BinaryExpression(val name: String, val operator: Operator, val value: Any?) {

        override fun toString() = "[${name}${operator}${
            if (value is String) {
                "`${value.replace("`", "\\`")}`"
            } else {
                value
            }
        }]"

        val matchNodeInfo: (nodeInfo: AccessibilityNodeInfo) -> Boolean = when (operator) {
            Operator.Start -> when (name) {
                "id" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.viewIdResourceName?.startsWith(value) == true })
                    else -> ({ false })
                }
                "text" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.text?.startsWith(value) == true })
                    else -> ({ false })
                }
                "hintText" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.hintText?.startsWith(value) == true })
                    else -> ({ false })
                }
                "contentDesc" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.contentDescription?.startsWith(value) == true })
                    else -> ({ false })
                }
                "className" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.className?.startsWith(value) == true })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.Include -> when (name) {
                "id" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.viewIdResourceName?.contains(value) == true })
                    else -> ({ false })
                }
                "text" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.text?.contains(value) == true })
                    else -> ({ false })
                }
                "hintText" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.hintText?.contains(value) == true })
                    else -> ({ false })
                }
                "contentDesc" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.contentDescription?.contains(value) == true })
                    else -> ({ false })
                }
                "className" -> when (value) {
                    is String -> ({ nodeInfo -> nodeInfo.className?.contains(value) == true })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.End -> {
                when (name) {
                    "id" -> when (value) {
                        is String -> ({ nodeInfo -> nodeInfo.viewIdResourceName?.endsWith(value) == true })
                        else -> ({ false })
                    }
                    "text" -> when (value) {
                        is String -> ({ nodeInfo -> nodeInfo.text?.endsWith(value) == true })
                        else -> ({ false })
                    }
                    "hintText" -> when (value) {
                        is String -> ({ nodeInfo -> nodeInfo.hintText?.endsWith(value) == true })
                        else -> ({ false })
                    }
                    "contentDesc" -> when (value) {
                        is String -> ({ nodeInfo -> nodeInfo.contentDescription?.endsWith(value) == true })
                        else -> ({ false })
                    }
                    "className" -> when (value) {
                        is String -> ({ nodeInfo -> nodeInfo.className?.endsWith(value) == true })
                        else -> ({ false })
                    }
                    else -> ({ false })
                }
            }
            Operator.Less -> when (name) {
                "index" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getIndex()?.let { it < value } == true })
                    else -> ({ false })
                }
                "childCount" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.childCount < value })
                    else -> ({ false })
                }
                "depth" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getDepth() < value })
                    else -> ({ false })
                }
                "text.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.text?.length?.let { it < value } == true })
                    else -> ({ false })
                }
                "hintText.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.hintText?.length?.let { it < value } == true })
                    else -> ({ false })
                }
                "contentDesc.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.contentDescription?.length?.let { it < value } == true })
                    else -> ({ false })
                }
                "className.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.className?.length?.let { it < value } == true })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.LessEqual -> when (name) {
                "index" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getIndex()?.let { it <= value } == true })
                    else -> ({ false })
                }
                "childCount" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.childCount <= value })
                    else -> ({ false })
                }
                "depth" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getDepth() <= value })
                    else -> ({ false })
                }
                "text.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.text?.length?.let { it <= value } == true })
                    else -> ({ false })
                }
                "hintText.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.hintText?.length?.let { it <= value } == true })
                    else -> ({ false })
                }
                "contentDesc.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.contentDescription?.length?.let { it <= value } == true })
                    else -> ({ false })
                }
                "className.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.className?.length?.let { it <= value } == true })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.More -> when (name) {
                "index" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getIndex()?.let { it > value } == true })
                    else -> ({ false })
                }
                "childCount" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.childCount > value })
                    else -> ({ false })
                }
                "depth" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getDepth() > value })
                    else -> ({ false })
                }
                "text.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.text?.length?.let { it > value } == true })
                    else -> ({ false })
                }
                "hintText.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.hintText?.length?.let { it > value } == true })
                    else -> ({ false })
                }
                "contentDesc.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.contentDescription?.length?.let { it > value } == true })
                    else -> ({ false })
                }
                "className.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.className?.length?.let { it > value } == true })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.MoreEqual -> when (name) {
                "index" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getIndex()?.let { it >= value } == true })
                    else -> ({ false })
                }
                "childCount" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.childCount >= value })
                    else -> ({ false })
                }
                "depth" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.getDepth() >= value })
                    else -> ({ false })
                }
                "text.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.text?.length?.let { it >= value } == true })
                    else -> ({ false })
                }
                "hintText.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.hintText?.length?.let { it >= value } == true })
                    else -> ({ false })
                }
                "contentDesc.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.contentDescription?.length?.let { it >= value } == true })
                    else -> ({ false })
                }
                "className.length" -> when (value) {
                    is Int -> ({ nodeInfo -> nodeInfo.className?.length?.let { it >= value } == true })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.Equal -> when (name) {
                "index" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.getIndex() == value })
                    else -> ({ false })
                }
                "childCount" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.getIndex() == value })
                    else -> ({ false })
                }
                "depth" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.getDepth() == value })
                    else -> ({ false })
                }
                "text" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.text == value })
                    else -> ({ false })
                }
                "text.length" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.text?.length == value })
                    else -> ({ false })
                }
                "hintText" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.hintText == value })
                    else -> ({ false })
                }
                "hintText.length" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.hintText?.length == value })
                    else -> ({ false })
                }
                "contentDesc" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.contentDescription == value })
                    else -> ({ false })
                }
                "contentDesc.length" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.contentDescription?.length == value })
                    else -> ({ false })
                }
                "isPassword" -> when (value) {
                    is Boolean? -> ({ nodeInfo -> nodeInfo.isPassword == value })
                    else -> ({ false })
                }
                "isChecked" -> when (value) {
                    is Boolean? -> ({ nodeInfo -> nodeInfo.isChecked == value })
                    else -> ({ false })
                }
                "className" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.className == value })
                    else -> ({ false })
                }
                else -> ({ false })
            }
            Operator.NotEqual -> when (name) {
                "index" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.getIndex() != value })
                    else -> ({ false })
                }
                "childCount" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.getIndex() != value })
                    else -> ({ false })
                }
                "depth" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.getDepth() != value })
                    else -> ({ false })
                }
                "text" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.text != value })
                    else -> ({ false })
                }
                "text.length" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.text?.length != value })
                    else -> ({ false })
                }
                "hintText" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.hintText != value })
                    else -> ({ false })
                }
                "hintText.length" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.hintText?.length != value })
                    else -> ({ false })
                }
                "contentDesc" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.contentDescription != value })
                    else -> ({ false })
                }
                "contentDesc.length" -> when (value) {
                    is Int? -> ({ nodeInfo -> nodeInfo.contentDescription?.length != value })
                    else -> ({ false })
                }
                "isPassword" -> when (value) {
                    is Boolean? -> ({ nodeInfo -> nodeInfo.isPassword != value })
                    else -> ({ false })
                }
                "isChecked" -> when (value) {
                    is Boolean? -> ({ nodeInfo -> nodeInfo.isChecked != value })
                    else -> ({ false })
                }
                "className" -> when (value) {
                    is String? -> ({ nodeInfo -> nodeInfo.className != value })
                    else -> ({ false })
                }
                else -> ({ false })
            }
        }
    }

}
