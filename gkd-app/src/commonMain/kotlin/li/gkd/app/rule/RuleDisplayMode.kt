package li.gkd.app.rule

/** Explicit values are part of the persisted settings contract. */
enum class RuleDisplayMode(val value: Int) {
    Flat(0), ByCategory(1);

    companion object {
        fun switch(value: Int): Int =
            if (value == ByCategory.value) Flat.value else ByCategory.value
    }
}
