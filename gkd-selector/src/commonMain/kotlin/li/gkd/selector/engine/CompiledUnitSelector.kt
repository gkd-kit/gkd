package li.gkd.selector.engine

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOptions
import li.gkd.selector.MatchContext
import li.gkd.selector.SelectorMatchStep
import li.gkd.selector.SelectorMatchUnit
import li.gkd.selector.SelectorSourceMap
import li.gkd.selector.SelectorTypeErrorKind
import li.gkd.selector.NodeAdapter
import li.gkd.selector.TraversalCandidate
import li.gkd.selector.TypeCheckFailure
import li.gkd.selector.getFastQueryDescendantsExcludingSelf
import li.gkd.selector.relation.RelationOperator
import li.gkd.selector.relation.RelationSelector
import li.gkd.selector.property.PropertySelector
import li.gkd.selector.property.TypeCheckCollector
import li.gkd.selector.property.TypeInferenceResult
import li.gkd.selector.property.inferType
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

    val targetIndex: Int = propertySelectors.indexOfLast { it.at }.takeIf { it >= 0 }
        ?: propertySelectors.lastIndex

    val fastQueryList: List<FastQuery> = propertySelectors.last().fastQueryList.orEmpty()

    val isMatchRoot: Boolean = propertySelectors.last().isMatchRoot

    // Snapshot stability comes from NodeAdapter. This only excludes states whose result also depends
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
        val matchedContext = matchPath(context, adapter, options) ?: return null
        return matchedContext.get(targetIndex).current
    }

    fun <T : Any> matchWithTrace(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): SelectorMatchUnit<T>? {
        val matchedContext = matchPath(context, adapter, options) ?: return null
        val matchingContexts = matchedContext.toContextList().reversed()
        val steps = Array(relations.size) { pathIndex ->
            val relationIndex = relations.lastIndex - pathIndex
            val relation = relations[relationIndex]
            val sourceContext = matchingContexts[pathIndex]
            val targetContext = matchingContexts[pathIndex + 1]
            val offset = resolveTraceOffset(
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
            steps = steps,
            range = range,
        )
    }

    fun hasSlowTraversal(options: MatchOptions): Boolean = relations.indices.any { index ->
            val relation = relations[index]
            relation.operator == RelationOperator.Descendant &&
                    !(options.fastQuery && propertySelectors[index].fastQueryList?.isNotEmpty() == true)
        }

    fun collectTypeFailures(collector: TypeCheckCollector) {
        for (propertySelector in propertySelectors) {
            for (expression in propertySelector.comparisonExpressions) {
                val operatorValid = expression.operator.allowType(
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
                            actual = "${expression.left.syntaxTypeName} and ${expression.right.syntaxTypeName}",
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

    private fun <T : Any> matchPath(
        initialContext: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): MatchContext<T>? {
        if (propertySelectors.size == 1) {
            return initialContext.takeIf { context ->
                propertySelectors[0].match(context, adapter)
            }
        }
        var propertySelectorIndex = propertySelectors.lastIndex
        var context = initialContext
        val stack = mutableListOf<SearchFrame<T>>()
        val failedStateKeysByPropertySelector = if (cacheablePropertySelectorCount > 0) {
            arrayOfNulls<MutableSet<Any>>(cacheablePropertySelectorCount)
        } else {
            null
        }

        fun isKnownFailure(index: Int, node: T): Boolean =
            index < cacheablePropertySelectorCount &&
                    failedStateKeysByPropertySelector?.get(index)
                        ?.contains(adapter.getNodeKey(node)) == true

        fun rememberFailure(index: Int, node: T) {
            if (index >= cacheablePropertySelectorCount) return
            val failedStateKeys = failedStateKeysByPropertySelector ?: return
            val propertySelectorFailures = failedStateKeys[index]
                ?: mutableSetOf<Any>().also { failedStateKeys[index] = it }
            propertySelectorFailures.add(adapter.getNodeKey(node))
        }

        while (true) {
            if (!isKnownFailure(propertySelectorIndex, context.current) &&
                propertySelectors[propertySelectorIndex].match(context, adapter)
            ) {
                if (propertySelectorIndex == 0) return context
                val relationIndex = propertySelectorIndex - 1
                stack.add(
                    SearchFrame(
                        propertySelectorIndex = propertySelectorIndex,
                        context = context,
                        candidates = candidates(
                            relation = relations[relationIndex],
                            target = propertySelectors[relationIndex],
                            context = context,
                            adapter = adapter,
                            options = options,
                        ).iterator(),
                    ),
                )
            } else {
                rememberFailure(propertySelectorIndex, context.current)
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
                rememberFailure(frame.propertySelectorIndex, frame.context.current)
                stack.removeAt(stack.lastIndex)
            }
            if (!advanced) return null
        }
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
            relation.isMatchAnyDescendant &&
            options.fastQuery &&
            fastQueries?.isNotEmpty() == true
        ) {
            return adapter.getFastQueryDescendantsExcludingSelf(context.current, fastQueries)
                .map { TraversalCandidate(it, -1) }
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
            relation.traversal(sourceContext, adapter)
                .firstOrNull { candidate -> adapter.getNodeKey(candidate.node) == targetKey },
        ) {
            "Matched relation target is missing from the adapter traversal"
        }.offset
    }
}
