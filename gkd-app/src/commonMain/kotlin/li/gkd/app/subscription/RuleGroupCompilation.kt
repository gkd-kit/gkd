package li.gkd.app.subscription

import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.SelectorTypeResult
import li.gkd.selector.createDefaultSelectorTypeModel

/** One complete validation result; failed groups never expose partially compiled selectors. */
class RuleGroupCompilation
private constructor(
    val error: RuleValidationError?,
    private val selectors: Map<String, Selector>,
) {
    fun selector(source: String): Selector {
        check(error == null) { "Cannot execute an invalid rule group: $error" }
        return checkNotNull(selectors[source]) {
            "Selector is not part of this rule group: $source"
        }
    }

    companion object {
        fun compile(group: RawSubscription.RawGroupProps): RuleGroupCompilation {
            val selectors = mutableMapOf<String, Selector>()
            val typeModel = createDefaultSelectorTypeModel()
            for (rule in group.rules) {
                for (source in rule.getAllSelectorStrings()) {
                    if (source in selectors) continue
                    val selector =
                        when (val result = Selector.compile(source)) {
                            is SelectorCompileResult.Success -> result.value
                            is SelectorCompileResult.Failure ->
                                return failure(
                                    RuleValidationError.SelectorError(source, result.error.message),
                                )
                        }
                    when (val result = selector.validateType(typeModel)) {
                        is SelectorTypeResult.Success -> selectors[source] = result.value
                        is SelectorTypeResult.Failure ->
                            return failure(
                                RuleValidationError.SelectorError(source, result.error.message),
                            )
                    }
                }
            }
            when (group) {
                is RawSubscription.RawAppGroup ->
                    versionPatternError(group, "versionName.pattern")?.let {
                        return failure(it)
                    }
                is RawSubscription.RawGlobalGroup ->
                    for ((index, app) in group.apps.orEmpty().withIndex()) {
                        versionPatternError(app, "apps[$index].versionName.pattern")?.let {
                            return failure(it)
                        }
                    }
            }
            for ((index, rule) in group.rules.withIndex()) {
                when (rule) {
                    is RawSubscription.RawAppRule ->
                        versionPatternError(rule, "rules[$index].versionName.pattern")?.let {
                            return failure(it)
                        }
                    is RawSubscription.RawGlobalRule ->
                        for ((appIndex, app) in rule.apps.orEmpty().withIndex()) {
                            versionPatternError(
                                    app,
                                    "rules[$index].apps[$appIndex].versionName.pattern",
                                )
                                ?.let {
                                    return failure(it)
                                }
                        }
                }
                rule.position?.validationError?.let {
                    return failure(
                        RuleValidationError.InvalidPosition(it.at("rules[$index].position")),
                    )
                }
                rule.swipeArg?.let { swipe ->
                    for ((field, position) in listOf("start" to swipe.start, "end" to swipe.end)) {
                        position?.validationError?.let {
                            return failure(
                                RuleValidationError.InvalidPosition(
                                    it.at("rules[$index].swipeArg.$field"),
                                ),
                            )
                        }
                    }
                }
            }
            return RuleGroupCompilation(null, selectors.toMap())
        }

        private fun failure(error: RuleValidationError) = RuleGroupCompilation(error, emptyMap())

        private fun versionPatternError(
            props: RawSubscription.RawAppRuleProps,
            field: String,
        ): RuleValidationError.InvalidVersionPattern? {
            val matcher = props.versionName ?: return null
            val source = matcher.pattern ?: return null
            val detail = matcher.patternError ?: return null
            return RuleValidationError.InvalidVersionPattern(field, source, detail)
        }
    }
}
