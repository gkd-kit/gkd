package li.gkd.app.matching.diagnostics

import java.util.IdentityHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.matching.query.matchRuleSelectorsUsing
import li.gkd.app.rule.ResolvedRule
import li.gkd.selector.Selector
import li.gkd.selector.SelectorEvaluation
import li.gkd.selector.SelectorEvaluationKind
import li.gkd.selector.SelectorObserver

fun <N : Any> matchRuleSelectorsExplained(
    rule: ResolvedRule,
    node: N,
    root: () -> N?,
    query: (N, Selector) -> N?,
    observer: RuleSelectorDiagnostics<N>,
): N? =
    matchRuleSelectorsUsing(
        rule.matchRoot,
        rule.anyMatches,
        rule.matches,
        rule.excludeMatches,
        rule.excludeAllMatches,
        node,
        root,
        { target, selector, group, index ->
            observer.begin(selector, "$group[$index]")
            var completed = false
            try {
                query(target, selector).also { completed = true }
            } finally {
                if (!completed) observer.interrupted()
            }
        },
        observer::outcome,
    )

@Serializable
data class MatchObservedValue(val type: String, val value: String?, val truncated: Boolean = false)

@Serializable
data class MatchConditionTrace(
    val selectorPart: String,
    val selector: String,
    val node: Int,
    val unit: Int?,
    val property: Int?,
    @Serializable(with = SelectorEvaluationKindSerializer::class) val kind: SelectorEvaluationKind,
    val expression: String,
    val matched: Boolean?,
    val operator: String?,
    val left: MatchObservedValue?,
    val right: MatchObservedValue?,
)

/** One bounded, detailed rule evaluation. No wrapper nodes and no attribute reads for labels. */
class RuleSelectorDiagnostics<N : Any>(
    private val diagnostics: MatchDiagnostics,
    private val sessionId: String,
    private val runId: Long,
    private val rule: String,
) : SelectorObserver<N> {
    private var remaining = 200
    private var selector = ""
    private var selectorPart: String? = null
    private val nodes = IdentityHashMap<N, Int>()

    override val enabled: Boolean
        get() = remaining > 0 && diagnostics.enabled && diagnostics.state.value?.id == sessionId

    fun begin(selector: Selector, part: String) {
        selectorPart = part
        if (enabled) this.selector = selector.toString().take(2048)
    }

    override fun record(event: SelectorEvaluation<N>) {
        if (!enabled) return
        val node = nodes.getOrPut(event.node) { nodes.size + 1 }
        val values =
            event.kind == SelectorEvaluationKind.Comparison ||
                event.kind == SelectorEvaluationKind.Name
        emit(
            MatchConditionTrace(
                checkNotNull(selectorPart),
                selector,
                node,
                event.unit,
                event.property,
                event.kind,
                event.expression.take(2048),
                event.matched,
                event.operator,
                if (values) observed(event.left) else null,
                if (values) observed(event.right) else null,
            ),
        )
        remaining--
        if (remaining == 0)
            emit(
                MatchConditionTrace(
                    checkNotNull(selectorPart),
                    selector,
                    node,
                    null,
                    null,
                    SelectorEvaluationKind.LimitReached,
                    "200 conditions; remaining evaluation is not explained",
                    null,
                    null,
                    null,
                    null,
                ),
            )
    }

    fun outcome(reason: String) {
        selectorPart = null
        if (!diagnostics.enabled || diagnostics.state.value?.id != sessionId) return
        diagnostics.record(runId, MatchStage.Selector, reason, rule, sessionId = sessionId)
    }

    fun interrupted() {
        selectorPart?.let { outcome("$it:incomplete") }
    }

    private fun emit(detail: MatchConditionTrace) {
        diagnostics.record(
            runId,
            MatchStage.Selector,
            describe(detail),
            rule,
            condition = detail,
            sessionId = sessionId,
        )
    }

    private fun observed(value: Any?): MatchObservedValue =
        when (value) {
            null -> MatchObservedValue("null", null)
            is CharSequence ->
                MatchObservedValue("string", value.take(256).toString(), value.length > 256)
            is Boolean -> MatchObservedValue("boolean", value.toString())
            is Number -> MatchObservedValue("number", value.toString())
            else -> MatchObservedValue("reference", null) // Never call arbitrary node.toString().
        }

    private fun describe(value: MatchConditionTrace): String = buildString {
        append("${value.selectorPart} node=${value.node}")
        value.unit?.let { append(" unit=$it") }
        value.property?.let { append(" property=$it") }
        append(" ${value.kind.key}: ${value.expression}")
        value.left?.let {
            append(" left=${display(it)} right=${display(checkNotNull(value.right))}")
        }
        append(" -> ${value.matched?.toString() ?: "not_evaluated"}")
    }

    private fun display(value: MatchObservedValue): String =
        when (value.type) {
            "string" -> JsonPrimitive(value.value).toString() + if (value.truncated) "…" else ""
            "reference" -> "<reference>"
            else -> value.value ?: "null"
        }
}

private object SelectorEvaluationKindSerializer : KSerializer<SelectorEvaluationKind> {
    override val descriptor =
        PrimitiveSerialDescriptor("SelectorEvaluationKind", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SelectorEvaluationKind) =
        encoder.encodeString(value.key)

    override fun deserialize(decoder: Decoder): SelectorEvaluationKind {
        val key = decoder.decodeString()
        return SelectorEvaluationKind.entries.firstOrNull { it.key == key }
            ?: throw SerializationException("Unknown selector evaluation kind: $key")
    }
}
