package li.songe.selector.data

sealed class CompareOperator(val key: String) {
    abstract fun compare(left: Any?, right: Any?): Boolean
    abstract fun allowType(type: PrimitiveValue): Boolean

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                Equal,
                NotEqual,
                Start, NotStart, Include, NotInclude, End, NotEnd, Less, LessEqual, More, MoreEqual
            ).sortedBy { -it.key.length }.toTypedArray()
        }

        // example
        // id="com.lptiyu.tanke:id/ab1"
        // id="com.lptiyu.tanke:id/ab2"
        private fun CharSequence.contentReversedEquals(other: CharSequence): Boolean {
            if (this === other) return true
            if (this.length != other.length) return false
            for (i in this.length - 1 downTo 0) {
                if (this[i] != other[i]) return false
            }
            return true
        }
    }

    data object Equal : CompareOperator("=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) {
                left.contentReversedEquals(right)
            } else {
                left == right
            }
        }

        override fun allowType(type: PrimitiveValue) = true
    }

    data object NotEqual : CompareOperator("!=") {
        override fun compare(left: Any?, right: Any?) = !Equal.compare(left, right)
        override fun allowType(type: PrimitiveValue) = true
    }

    data object Start : CompareOperator("^=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) left.startsWith(right) else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.StringValue
    }

    data object NotStart : CompareOperator("!^=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) !left.startsWith(right) else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.StringValue
    }

    data object Include : CompareOperator("*=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) left.contains(right) else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.StringValue
    }

    data object NotInclude : CompareOperator("!*=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) !left.contains(right) else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.StringValue
    }

    data object End : CompareOperator("$=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) left.endsWith(right) else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.StringValue
    }

    data object NotEnd : CompareOperator("!$=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) !left.endsWith(right) else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.StringValue
    }

    data object Less : CompareOperator("<") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left < right else false
        }


        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.IntValue
    }

    data object LessEqual : CompareOperator("<=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left <= right else false
        }

        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.IntValue
    }

    data object More : CompareOperator(">") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left > right else false
        }


        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.IntValue
    }

    data object MoreEqual : CompareOperator(">=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left >= right else false
        }


        override fun allowType(type: PrimitiveValue) = type is PrimitiveValue.IntValue
    }

}