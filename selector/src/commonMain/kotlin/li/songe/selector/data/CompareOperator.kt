package li.songe.selector.data

sealed class CompareOperator(val key: String) {
    override fun toString() = key
    abstract fun compare(left: Any?, right: Any?): Boolean

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                Equal,
                NotEqual,
                Start, NotStart, Include, NotInclude, End, NotEnd, Less, LessEqual, More, MoreEqual
            ).sortedBy { -it.key.length }
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
    }

    data object NotEqual : CompareOperator("!=") {
        override fun compare(left: Any?, right: Any?) = !Equal.compare(left, right)
    }

    data object Start : CompareOperator("^=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) left.startsWith(right) else false
        }
    }

    data object NotStart : CompareOperator("!^=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) !left.startsWith(right) else false
        }
    }

    data object Include : CompareOperator("*=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) left.contains(right) else false
        }
    }

    data object NotInclude : CompareOperator("!*=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) !left.contains(right) else false
        }
    }

    data object End : CompareOperator("$=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) left.endsWith(right) else false
        }
    }

    data object NotEnd : CompareOperator("!$=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is CharSequence && right is CharSequence) !left.endsWith(right) else false
        }
    }

    data object Less : CompareOperator("<") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left < right else false
        }
    }

    data object LessEqual : CompareOperator("<=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left <= right else false
        }
    }

    data object More : CompareOperator(">") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left > right else false
        }
    }

    data object MoreEqual : CompareOperator(">=") {
        override fun compare(left: Any?, right: Any?): Boolean {
            return if (left is Int && right is Int) left >= right else false
        }
    }

}