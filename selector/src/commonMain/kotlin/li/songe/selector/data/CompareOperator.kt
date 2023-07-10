package li.songe.selector.data

sealed class CompareOperator(val key: String) {
    override fun toString() = key
    abstract fun compare(a: Any?, b: Any?): Boolean

    companion object {
        val allSubClasses = listOf(
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
            MoreEqual
        ).sortedBy { -it.key.length }
    }

    object Equal : CompareOperator("=") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is CharSequence && b is CharSequence) a.contentEquals(b) else a == b
        }
    }

    object NotEqual : CompareOperator("!=") {
        override fun compare(a: Any?, b: Any?) = !Equal.compare(a, b)
    }

    object Start : CompareOperator("^=") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is CharSequence && b is CharSequence) a.startsWith(b) else false
        }
    }

    object NotStart : CompareOperator("!^=") {
        override fun compare(a: Any?, b: Any?) = !Start.compare(a, b)
    }

    object Include : CompareOperator("*=") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is CharSequence && b is CharSequence) a.contains(b) else false
        }
    }

    object NotInclude : CompareOperator("!*=") {
        override fun compare(a: Any?, b: Any?) = !Include.compare(a, b)
    }

    object End : CompareOperator("$=") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is CharSequence && b is CharSequence) a.endsWith(b) else false
        }
    }

    object NotEnd : CompareOperator("!$=") {
        override fun compare(a: Any?, b: Any?) = !End.compare(a, b)
    }

    object Less : CompareOperator("<") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is Int && b is Int) a < b else false
        }
    }

    object LessEqual : CompareOperator("<=") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is Int && b is Int) a <= b else false
        }
    }

    object More : CompareOperator(">") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is Int && b is Int) a > b else false
        }
    }

    object MoreEqual : CompareOperator(">=") {
        override fun compare(a: Any?, b: Any?): Boolean {
            return if (a is Int && b is Int) a >= b else false
        }
    }

}