package li.gkd.app.matching.diagnostics

import java.util.IdentityHashMap
import kotlinx.serialization.Serializable
import li.gkd.app.matching.query.querySingleSelector
import li.gkd.app.rule.ResolvedRule
import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.TraversalCandidate
import li.gkd.selector.relation.RelationExpression

@Serializable
data class MatchSelectorPlan(
    val matches: List<String>,
    val anyMatches: List<String> = emptyList(),
    val excludeMatches: List<String> = emptyList(),
    val excludeAllMatches: List<String> = emptyList(),
    val matchRoot: Boolean = false,
    val fastQuery: Boolean = false,
    val rootOnlySelectors: Boolean = false,
)

@Serializable
data class MatchRecordedValue(
    val type: MatchRecordedValueType,
    val value: String = "",
    val reference: Int = 0,
    val offset: Int = 0,
    val equality: Int = 0,
)

@Serializable
data class MatchNodeCall(
    val operation: MatchNodeOperation,
    val arguments: List<MatchRecordedValue>,
    val startedNanos: Long,
    val durationNanos: Long,
    val thread: String,
    val result: MatchRecordedValue? = null,
    val failure: String? = null,
    val failureMessage: String? = null,
    val operationId: Long? = null,
)

@Serializable
data class MatchQueryRecording(
    val runId: Long,
    val rule: String,
    val plan: MatchSelectorPlan,
    val source: MatchRecordedValue,
    val calls: List<MatchNodeCall>,
    val result: MatchRecordedValue?,
    val failure: String?,
    val incomplete: String?,
    val operationId: Long? = null,
    val completed: Boolean = true,
    val startedAt: Long? = null,
    val startedNanos: Long? = null,
    val endedAt: Long? = null,
    val durationNanos: Long? = null,
    val outcome: String? = null,
    val operations: List<MatchOperation> = emptyList(),
)

/**
 * Explicit recording only. Delegates exactly once, preserving lazy iteration and original nodes.
 */
class RecordingNodeAdapter<N : Any>(
    private val delegate: NodeAdapter<N>,
    private val diagnostics: MatchDiagnostics? = null,
    private val queryOperation: MatchOperation? = null,
    private val nanoTime: () -> Long = System::nanoTime,
) : NodeAdapter<N>() {
    private val origin = nanoTime()
    private val startedAt = System.currentTimeMillis()
    private val objects = IdentityHashMap<Any, Int>()
    private val equalObjects = mutableMapOf<Any, Int>()
    private val keys = mutableMapOf<Any, Int>()
    val evidence = MatchQueryEvidence()
    private var sequence = 0
    val incomplete: String?
        get() = evidence.incomplete

    fun value(value: Any?): MatchRecordedValue =
        when (value) {
            null -> MatchRecordedValue(MatchRecordedValueType.Null)
            is CharSequence -> {
                if (value.length > 16_384) evidence.truncate("value_limit")
                MatchRecordedValue(MatchRecordedValueType.String, value.take(16_384).toString())
            }
            is Boolean -> MatchRecordedValue(MatchRecordedValueType.Boolean, value.toString())
            is Int -> MatchRecordedValue(MatchRecordedValueType.Int, value.toString())
            is Long -> MatchRecordedValue(MatchRecordedValueType.Long, value.toString())
            is Float -> MatchRecordedValue(MatchRecordedValueType.Float, value.toString())
            is Double -> MatchRecordedValue(MatchRecordedValueType.Double, value.toString())
            is TraversalCandidate<*> ->
                MatchRecordedValue(
                    MatchRecordedValueType.Candidate,
                    reference = reference(value.node),
                    offset = value.offset,
                    equality = equality(value.node),
                )
            else ->
                MatchRecordedValue(
                    MatchRecordedValueType.Reference,
                    reference = reference(value),
                    equality = equality(value),
                )
        }

    private fun reference(value: Any): Int = objects.getOrPut(value) { objects.size + 1 }

    private fun equality(value: Any): Int = equalObjects.getOrPut(value) { equalObjects.size + 1 }

    private fun <T> call(
        operation: MatchNodeOperation,
        args: List<Any?>,
        encode: (T) -> MatchRecordedValue = { value(it) },
        block: () -> T,
    ): T {
        if (incomplete != null) return block()
        val arguments = args.map(::value)
        val operationTrace =
            when (operation) {
                MatchNodeOperation.Key,
                MatchNodeOperation.Name -> null
                else ->
                    queryOperation?.let {
                        diagnostics?.beginOperation(
                            "Selector.${operation.wireValue}",
                            it.runId,
                            it.rule,
                            parentOperationId = it.id,
                            arguments = arguments,
                            sessionId = it.sessionId,
                            recording = evidence,
                        )
                    }
            }
        val started = nanoTime()
        var outcome = "returned"
        return try {
            val result =
                MatchNodeObservation.observe(operationTrace, ::value, block, recording = evidence)
            val duration = nanoTime() - started
            evidence.append(
                MatchNodeCall(
                    operation,
                    arguments,
                    started - origin,
                    duration,
                    Thread.currentThread().name,
                    result = encode(result),
                    operationId = operationTrace?.id,
                ),
            )
            result
        } catch (error: Exception) {
            outcome = error.toString()
            evidence.append(
                MatchNodeCall(
                    operation,
                    arguments,
                    started - origin,
                    nanoTime() - started,
                    Thread.currentThread().name,
                    failure = error.javaClass.name,
                    failureMessage = error.message,
                    operationId = operationTrace?.id,
                ),
            )
            throw error
        } finally {
            diagnostics?.endOperation(operationTrace, outcome)
        }
    }

    fun root(block: () -> N?): N? = call(MatchNodeOperation.QueryRoot, emptyList(), block = block)

    fun begin(runId: Long, rule: String, plan: MatchSelectorPlan, source: MatchRecordedValue) =
        evidence.begin(
            MatchQueryRecording(
                runId,
                rule,
                plan,
                source,
                emptyList(),
                null,
                null,
                null,
                operationId = queryOperation?.id,
                completed = false,
                startedAt = startedAt,
                startedNanos = queryOperation?.startedNanos,
            ),
        )

    fun finish(result: N?, failure: String?, cancelled: Boolean = false) =
        evidence.finish(
            value(result),
            failure,
            System.currentTimeMillis(),
            nanoTime() - origin,
            if (failure == null) {
                if (result == null) "not_matched" else "matched"
            } else if (cancelled) "cancelled" else "failed",
        )

    private fun <T> stream(
        operation: MatchNodeOperation,
        args: List<Any?>,
        block: () -> Sequence<T>,
    ): Sequence<T> {
        if (incomplete != null) return block()
        val id = ++sequence
        val source = call(operation, args, { value(id) }, block)
        return Sequence {
            val iterator =
                call(MatchNodeOperation.Iterator, listOf(id), { value(id) }) { source.iterator() }
            object : Iterator<T> {
                override fun hasNext() =
                    call(MatchNodeOperation.HasNext, listOf(id)) { iterator.hasNext() }

                override fun next() = call(MatchNodeOperation.Next, listOf(id)) { iterator.next() }
            }
        }
    }

    override fun getAttr(target: Any, name: String) =
        call(MatchNodeOperation.Attr, listOf(target, name)) { delegate.getAttr(target, name) }

    override fun getInvoke(target: Any, name: String, args: List<Any>) =
        call(MatchNodeOperation.Invoke, listOf(target, name) + args) {
            delegate.getInvoke(target, name, args)
        }

    override fun getName(node: N) =
        call(MatchNodeOperation.Name, listOf(node)) { delegate.getName(node) }

    override fun getChildCount(node: N) =
        call(MatchNodeOperation.Count, listOf(node)) { delegate.getChildCount(node) }

    override fun getChild(node: N, index: Int) =
        call(MatchNodeOperation.Child, listOf(node, index)) { delegate.getChild(node, index) }

    override fun getParent(node: N) =
        call(MatchNodeOperation.Parent, listOf(node)) { delegate.getParent(node) }

    override fun getRoot(node: N) =
        call(MatchNodeOperation.Root, listOf(node)) { delegate.getRoot(node) }

    override fun getNodeKey(node: N) =
        call(
            MatchNodeOperation.Key,
            listOf(node),
            {
                MatchRecordedValue(
                    MatchRecordedValueType.Key,
                    reference = keys.getOrPut(it) { keys.size + 1 },
                )
            },
        ) {
            delegate.getNodeKey(node)
        }

    override fun getChildren(node: N) =
        stream(MatchNodeOperation.Children, listOf(node)) { delegate.getChildren(node) }

    override fun getDescendants(node: N) =
        stream(MatchNodeOperation.Descendants, listOf(node)) { delegate.getDescendants(node) }

    override fun getFastQueryDescendants(node: N, fastQueryList: List<FastQuery>) =
        stream(MatchNodeOperation.Fast, listOf(node) + fastQueryList.map { it.toString() }) {
            delegate.getFastQueryDescendants(node, fastQueryList)
        }

    override fun traverseChildren(node: N, relationExpression: RelationExpression) =
        stream(MatchNodeOperation.ChildRelation, relationArgs(node, relationExpression)) {
            delegate.traverseChildren(node, relationExpression)
        }

    override fun traverseAncestors(node: N, relationExpression: RelationExpression) =
        stream(MatchNodeOperation.AncestorRelation, relationArgs(node, relationExpression)) {
            delegate.traverseAncestors(node, relationExpression)
        }

    override fun traversePreviousSiblings(node: N, relationExpression: RelationExpression) =
        stream(MatchNodeOperation.PreviousRelation, relationArgs(node, relationExpression)) {
            delegate.traversePreviousSiblings(node, relationExpression)
        }

    override fun traverseFollowingSiblings(node: N, relationExpression: RelationExpression) =
        stream(MatchNodeOperation.FollowingRelation, relationArgs(node, relationExpression)) {
            delegate.traverseFollowingSiblings(node, relationExpression)
        }

    override fun traverseDescendants(node: N, relationExpression: RelationExpression) =
        stream(MatchNodeOperation.DescendantRelation, relationArgs(node, relationExpression)) {
            delegate.traverseDescendants(node, relationExpression)
        }
}

private fun relationArgs(node: Any, expression: RelationExpression) =
    listOf(node, expression.minOffset, expression.maxOffset, expression.matchesAllOffsets)

fun <N : Any> recordManualQuery(
    selector: Selector,
    options: MatchOptions,
    node: N,
    root: () -> N?,
    adapter: NodeAdapter<N>,
    diagnostics: MatchDiagnostics,
    session: MatchDiagnosticState,
    runId: Long,
): N? {
    val operation =
        diagnostics.beginOperation("SelectorQuery", runId, "manual", sessionId = session.id)
    val recording = RecordingNodeAdapter(adapter, diagnostics, operation)
    val source = recording.value(node)
    val plan =
        MatchSelectorPlan(
            listOf(selector.toString()),
            fastQuery = options.fastQuery,
            rootOnlySelectors = true,
        )
    recording.begin(runId, "manual", plan, source)
    diagnostics.registerQuery(session.id, recording.evidence)
    var result: N? = null
    var failure: String? = null
    var cancelled = false
    try {
        result =
            MatchNodeObservation.observe(
                operation,
                recording::value,
                {
                    querySingleSelector(
                        node,
                        selector,
                        { recording.root(root) },
                        recording,
                        options,
                        rootOnlySelectors = true,
                    )
                },
                sessionId = session.id,
            )
        return result
    } catch (error: Exception) {
        failure = error.javaClass.name
        cancelled =
            error is kotlinx.coroutines.CancellationException || error is InterruptedException
        throw error
    } finally {
        diagnostics.endOperation(
            operation,
            failure ?: if (result == null) "not_matched" else "matched",
        )
        recording.finish(result, failure, cancelled)
        diagnostics.finishQuery(session.id)
    }
}

fun <N : Any> recordRuleQuery(
    rule: ResolvedRule,
    node: N,
    root: () -> N?,
    adapter: NodeAdapter<N>,
    diagnostics: MatchDiagnostics,
    session: MatchDiagnosticState,
    runId: Long,
    rootOnlySelectors: Boolean,
): N? {
    val operation =
        diagnostics.beginOperation(
            "SelectorQuery",
            runId,
            rule.matchIdentity(),
            sessionId = session.id,
        )
    val recording = RecordingNodeAdapter(adapter, diagnostics, operation)
    val source = recording.value(node)
    val plan =
        MatchSelectorPlan(
            rule.matches.map { it.toString() },
            rule.anyMatches.map { it.toString() },
            rule.excludeMatches.map { it.toString() },
            rule.excludeAllMatches.map { it.toString() },
            rule.matchRoot,
            rule.matchOptions.fastQuery,
            rootOnlySelectors,
        )
    val observer =
        RuleSelectorDiagnostics<N>(
            diagnostics,
            session.id,
            runId,
            rule.matchIdentity(),
            captureConditions = session.detailed,
        )
    recording.begin(runId, rule.matchIdentity(), plan, source)
    diagnostics.registerQuery(session.id, recording.evidence)
    var result: N? = null
    var failure: String? = null
    var cancelled = false
    try {
        val query: (N, Selector) -> N? = { target, selector ->
            querySingleSelector(
                target,
                selector,
                { recording.root(root) },
                recording,
                rule.matchOptions,
                rootOnlySelectors,
                observer,
            )
        }
        result =
            MatchNodeObservation.observe(
                operation,
                recording::value,
                {
                    matchRuleSelectorsExplained(
                        rule,
                        node,
                        { recording.root(root) },
                        query,
                        observer,
                    )
                },
                sessionId = session.id,
            )
        return result
    } catch (error: Exception) {
        failure = error.javaClass.name
        cancelled =
            error is kotlinx.coroutines.CancellationException || error is InterruptedException
        throw error
    } finally {
        diagnostics.endOperation(
            operation,
            failure ?: if (result == null) "not_matched" else "matched",
        )
        recording.finish(result, failure, cancelled)
        diagnostics.finishQuery(session.id)
    }
}
