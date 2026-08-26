package li.songe.selector.property

import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import li.songe.selector.comparePrimitiveValue
import kotlin.js.JsExport


@JsExport
sealed class CompareOperator(val key: String) : Stringify {
    override fun stringify() = key

    abstract fun <T> compare(
        context: QueryContext<T>,
        transform: Transform<T>,
        leftExp: ValueExpression,
        rightExp: ValueExpression,
    ): Boolean

    internal abstract fun allowType(left: ValueExpression, right: ValueExpression): Boolean

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                Equal,
                NotEqual,
                Start,
                NotStart,
                Include,
                NotInclude,
                End,
                NotEnd,
                Less,
                LessEqual,
                More,
                MoreEqual,
                Matches,
                NotMatches
            ).sortedBy { -it.key.length }.toTypedArray()
        }

    }

    data object Equal : CompareOperator("=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,
        ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return comparePrimitiveValue(left, right)
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) = true

    }

    data object NotEqual : CompareOperator("!=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,
        ): Boolean {
            return !Equal.compare(context, transform, leftExp, rightExp)
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) = true
    }

    data object Start : CompareOperator("^=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is CharSequence && right is CharSequence) {
                left.startsWith(right)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression): Boolean {
            return (left is ValueExpression.StringLiteral || left is ValueExpression.Variable) && (right is ValueExpression.StringLiteral || right is ValueExpression.Variable)
        }
    }

    data object NotStart : CompareOperator("!^=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is CharSequence && right is CharSequence) {
                !left.startsWith(right)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Start.allowType(left, right)
    }

    data object Include : CompareOperator("*=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is CharSequence && right is CharSequence) {
                left.contains(right)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Start.allowType(left, right)
    }

    data object NotInclude : CompareOperator("!*=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is CharSequence && right is CharSequence) {
                !left.contains(right)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Start.allowType(left, right)
    }

    data object End : CompareOperator("$=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is CharSequence && right is CharSequence) {
                left.endsWith(right)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Start.allowType(left, right)
    }

    data object NotEnd : CompareOperator("!$=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is CharSequence && right is CharSequence) {
                !left.endsWith(
                    right
                )
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Start.allowType(left, right)
    }

    data object Less : CompareOperator("<") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is Int && right is Int) left < right else false
        }


        override fun allowType(left: ValueExpression, right: ValueExpression): Boolean {
            return (left is ValueExpression.Variable || left is ValueExpression.IntLiteral) && (right is ValueExpression.IntLiteral || right is ValueExpression.Variable)
        }
    }

    data object LessEqual : CompareOperator("<=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is Int && right is Int) left <= right else false
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Less.allowType(left, right)
    }

    data object More : CompareOperator(">") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is Int && right is Int) left > right else false
        }

        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Less.allowType(left, right)
    }

    data object MoreEqual : CompareOperator(">=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            val right = rightExp.getAttr(context, transform)
            return if (left is Int && right is Int) left >= right else false
        }


        override fun allowType(left: ValueExpression, right: ValueExpression) =
            Less.allowType(left, right)
    }

    data object Matches : CompareOperator("~=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            return if (left is CharSequence && rightExp is ValueExpression.StringLiteral) {
                rightExp.outMatches(left)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression): Boolean {
            return (left is ValueExpression.Variable) && (right is ValueExpression.StringLiteral && right.matches != null)
        }
    }

    data object NotMatches : CompareOperator("!~=") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            leftExp: ValueExpression,
            rightExp: ValueExpression,

            ): Boolean {
            val left = leftExp.getAttr(context, transform)
            return if (left is CharSequence && rightExp is ValueExpression.StringLiteral) {
                !rightExp.outMatches(left)
            } else {
                false
            }
        }

        override fun allowType(left: ValueExpression, right: ValueExpression): Boolean {
            return Matches.allowType(left, right)
        }
    }

}