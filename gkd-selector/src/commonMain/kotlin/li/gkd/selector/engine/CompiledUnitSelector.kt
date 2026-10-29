package li.gkd.selector.engine

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchContext
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.SelectorEvaluation
import li.gkd.selector.SelectorEvaluationKind
import li.gkd.selector.SelectorMatchStep
import li.gkd.selector.SelectorMatchUnit
import li.gkd.selector.SelectorObserver
import li.gkd.selector.SelectorSourceMap
import li.gkd.selector.SelectorTypeErrorKind
import li.gkd.selector.TraversalCandidate
import li.gkd.selector.TypeCheckFailure
import li.gkd.selector.getFastQueryDescendantsExcludingSelf
import li.gkd.selector.property.PropertySelector
import li.gkd.selector.property.TypeCheckCollector
import li.gkd.selector.property.TypeInferenceResult
import li.gkd.selector.property.inferType
import li.gkd.selector.relation.RelationOperator
import li.gkd.selector.relation.RelationSelector
import li.gkd.selector.syntax.SelectorPrinter

private class SearchFrame<T : Any>(
    val propertySelectorIndex: Int,
    val context: MatchContext<T>,
    val candidates: Iterator<TraversalCandidate<T>>,
)

internal class CompiledUnitSelector(
    expression: UnitSelectorExpression,
    private val sourceMap: SelectorSourceMap?,
) {
    private val propertySelectors = expression.propertySelectors
    private val relations = expression.relations
    private val range = sourceMap?.rangeOf(expression)

    val targetIndex: Int =
        propertySelectors.indexOfLast { it.at }.takeIf { it >= 0 } ?: propertySelectors.lastIndex

    val fastQueryList: List<FastQuery> = propertySelectors.last().fastQueryList.orEmpty()

    val isMatchRoot: Boolean = propertySelectors.last().isMatchRoot

    // Snapshot stability comes from NodeAdapter. This only excludes states whose result also
    // depends
    // on the path used to reach the current node.
    private val cacheablePropertySelectorCount: Int = run {
        var count = 0
        while (count < propertySelectors.size) {
            if (
                propertySelectors[count].usesPreviousContext ||
                    (count > 0 && relations[count - 1].operator == RelationOperator.Previous)
            ) {
                break
            }
            count++
        }
        count
    }

    fun <T : Any> match(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): T? {
        val matchedContext =
            matchPath(
                context,
                adapter,
                options,
                { index, candidate ->
                    propertySelectors[index].match(candidate, adapter)
                },
                { _, _, _ -> },
            ) ?: return null
        return matchedContext.get(targetIndex).current
    }

    fun <T : Any> matchExplained(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
        observer: SelectorObserver<T>,
        unit: Int,
    ): T? {
        val matched =
            matchPath(
                context,
                adapter,
                options,
                { index, candidate ->
                    propertySelectors[index].matchExplained(
                        candidate,
                        adapter,
                        observer,
                        unit,
                        index,
                    )
                },
                { index, candidate, reason ->
                    if (
                        observer.enabled &&
                            (reason != SelectorEvaluationKind.RelationSearch ||
                                options.fastQuery &&
                                    relations[index - 1].isMatchAnyDescendant &&
                                    propertySelectors[index - 1].fastQueryList?.isNotEmpty() ==
                                        true)
                    ) {
                        observer.record(
                            SelectorEvaluation(
                                node = candidate.current,
                                unit = unit,
                                property = index,
                                kind =
                                    if (reason == SelectorEvaluationKind.RelationSearch)
                                        SelectorEvaluationKind.FastQueryScope
                                    else reason,
                                expression =
                                    if (index > 0) SelectorPrinter.render(relations[index - 1])
                                    else "property",
                                matched =
                                    if (
                                        reason == SelectorEvaluationKind.CachedFailure ||
                                            reason == SelectorEvaluationKind.RelationExhausted
                                    )
                                        false
                                    else null,
                            ),
                        )
                    }
                },
            ) ?: return null
        return matched.get(targetIndex).current
    }

    fun <T : Any> matchWithTrace(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): SelectorMatchUnit<T>? {
        val matchedContext =
            matchPath(
                context,
                adapter,
                options,
                { index, candidate ->
                    propertySelectors[index].match(candidate, adapter)
                },
                { _, _, _ -> },
            ) ?: return null
        val matchingContexts = matchedContext.toContextList().reversed()
        val steps =
            Array(relations.size) { pathIndex ->
                val relationIndex = relations.lastIndex - pathIndex
                val relation = relations[relationIndex]
                val sourceContext = matchingContexts[pathIndex]
                val targetContext = matchingContexts[pathIndex + 1]
                val offset =
                    resolveTraceOffset(
                        relation = relation,
                        sourceContext = sourceContext,
                        targetContext = targetContext,
                        adapter = adapter,
                    )
                SelectorMatchStep(
                    source = sourceContext.current,
                    target = targetContext.current,
                    kind = relation.operator.kind,
                    offset = offset,
                    formattedRelation = relation.operator.formatOffset(offset),
                    sourceRange = sourceMap?.rangeOf(propertySelectors[relationIndex + 1]),
                    relationRange = sourceMap?.rangeOf(relation),
                    targetRange = sourceMap?.rangeOf(propertySelectors[relationIndex]),
                )
            }
        return SelectorMatchUnit(
            target = matchedContext.get(targetIndex).current,
            queryNode = context.current,
            fastQueries = fastQueryList,
            steps = steps,
            range = range,
        )
    }

    fun collectTypeFailures(collector: TypeCheckCollector) {
        for (propertySelector in propertySelectors) {
            for (expression in propertySelector.comparisonExpressions) {
                val operatorValid =
                    expression.operator.allowType(
                        expression.left,
                        expression.right,
                    )
                if (!operatorValid) {
                    collector.report(
                        TypeCheckFailure(
                            kind = SelectorTypeErrorKind.OperatorTypeMismatch,
                            positionValue = expression,
                            expression = SelectorPrinter.render(expression),
                            expected = expression.operator.expectedOperandDescription,
                            actual =
                                "${expression.left.syntaxTypeName} and ${expression.right.syntaxTypeName}",
                        ),
                    )
                    if (collector.isFull) return
                }
                val leftType = expression.left.inferType(collector)
                if (collector.isFull) return
                val rightType = expression.right.inferType(collector)
                if (collector.isFull) return
                if (
                    operatorValid &&
                        leftType is TypeInferenceResult.Known &&
                        rightType is TypeInferenceResult.Known &&
                        leftType.type != rightType.type
                ) {
                    collector.report(
                        TypeCheckFailure(
                            kind = SelectorTypeErrorKind.OperandTypeMismatch,
                            positionValue = expression,
                            expression = SelectorPrinter.render(expression),
                            expected = leftType.type.displayName,
                            actual = rightType.type.displayName,
                        ),
                    )
                    if (collector.isFull) return
                }
            }
        }
    }

    private inline fun <T : Any> matchPath(
        initialContext: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
        matchProperty: (Int, MatchContext<T>) -> Boolean,
        explain: (Int, MatchContext<T>, SelectorEvaluationKind) -> Unit,
    ): MatchContext<T>? {
        if (propertySelectors.size == 1) {
            return initialContext.takeIf { context ->
                matchProperty(0, context)
            }
        }
        var propertySelectorIndex = propertySelectors.lastIndex
        var context = initialContext
        val stack = mutableListOf<SearchFrame<T>>()
        val failedStateKeysByPropertySelector =
            if (cacheablePropertySelectorCount > 0) {
                arrayOfNulls<MutableSet<Any>>(cacheablePropertySelectorCount)
            } else {
                null
            }

        while (true) {
            val knownFailure =
                propertySelectorIndex < cacheablePropertySelectorCount &&
                    failedStateKeysByPropertySelector
                        ?.get(propertySelectorIndex)
                        ?.contains(adapter.getNodeKey(context.current)) == true
            val matched =
                if (knownFailure) {
                    explain(propertySelectorIndex, context, SelectorEvaluationKind.CachedFailure)
                    false
                } else matchProperty(propertySelectorIndex, context)
            if (matched) {
                if (propertySelectorIndex == 0) return context
                val relationIndex = propertySelectorIndex - 1
                explain(propertySelectorIndex, context, SelectorEvaluationKind.RelationSearch)
                stack.add(
                    SearchFrame(
                        propertySelectorIndex = propertySelectorIndex,
                        context = context,
                        candidates =
                            candidates(
                                    relation = relations[relationIndex],
                                    target = propertySelectors[relationIndex],
                                    context = context,
                                    adapter = adapter,
                                    options = options,
                                )
                                .iterator(),
                    ),
                )
            } else {
                rememberFailure(
                    failedStateKeysByPropertySelector,
                    propertySelectorIndex,
                    context.current,
                    adapter,
                )
            }

            var advanced = false
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                if (frame.candidates.hasNext()) {
                    val candidate = frame.candidates.next()
                    context = frame.context.next(candidate.node, candidate.offset)
                    propertySelectorIndex = frame.propertySelectorIndex - 1
                    advanced = true
                    break
                }
                explain(
                    frame.propertySelectorIndex,
                    frame.context,
                    SelectorEvaluationKind.RelationExhausted,
                )
                rememberFailure(
                    failedStateKeysByPropertySelector,
                    frame.propertySelectorIndex,
                    frame.context.current,
                    adapter,
                )
                stack.removeAt(stack.lastIndex)
            }
            if (!advanced) return null
        }
    }

    private fun <T : Any> rememberFailure(
        failedStateKeys: Array<MutableSet<Any>?>?,
        index: Int,
        node: T,
        adapter: NodeAdapter<T>,
    ) {
        if (index >= cacheablePropertySelectorCount || failedStateKeys == null) return
        val failures =
            failedStateKeys[index] ?: mutableSetOf<Any>().also { failedStateKeys[index] = it }
        failures.add(adapter.getNodeKey(node))
    }

    private fun <T : Any> candidates(
        relation: RelationSelector,
        target: PropertySelector,
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): Sequence<TraversalCandidate<T>> {
        val fastQueries = target.fastQueryList
        if (relation.isMatchAnyAncestor && target.isMatchRoot) {
            val root = adapter.getRoot(context.current) ?: return emptySequence()
            if (adapter.getNodeKey(root) == adapter.getNodeKey(context.current)) {
                return emptySequence()
            }
            return sequenceOf(TraversalCandidate(root, -1))
        }
        if (
            relation.isMatchAnyDescendant && options.fastQuery && fastQueries?.isNotEmpty() == true
        ) {
            return adapter.getFastQueryDescendantsExcludingSelf(context.current, fastQueries).map {
                TraversalCandidate(it, -1)
            }
        }
        return relation.traversal(context, adapter)
    }

    private fun <T : Any> resolveTraceOffset(
        relation: RelationSelector,
        sourceContext: MatchContext<T>,
        targetContext: MatchContext<T>,
        adapter: NodeAdapter<T>,
    ): Int {
        if (targetContext.incomingOffset >= 0) return targetContext.incomingOffset
        val targetKey = adapter.getNodeKey(targetContext.current)
        return checkNotNull(
                relation.traversal(sourceContext, adapter).firstOrNull { candidate ->
                    adapter.getNodeKey(candidate.node) == targetKey
                },
            ) {
                "Matched relation target is missing from the adapter traversal"
            }
            .offset
    }
}
