package li.gkd.selector.engine

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOptions
import li.gkd.selector.LogicalOperator
import li.gkd.selector.MatchContext
import li.gkd.selector.SelectorMatch
import li.gkd.selector.SelectorSourceMap
import li.gkd.selector.NodeAdapter
import li.gkd.selector.TypeCheckFailure
import li.gkd.selector.SelectorType
import li.gkd.selector.property.TypeCheckCollector

private const val MATCH_UNIT = 0
private const val NOT = 1
private const val AND_ENTER = 2
private const val AND_EXIT = 3
private const val OR_ENTER = 4
private const val OR_EXIT = 5
private const val INSTRUCTION_SIZE = 3

internal class SelectorProgram private constructor(
    private val code: IntArray,
    private val units: List<CompiledUnitSelector>,
    val fastQueryList: List<FastQuery>,
    val isMatchRoot: Boolean,
) {
    fun <T : Any> match(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): T? {
        var programCounter = 0
        var result: T? = null
        while (programCounter < code.size / INSTRUCTION_SIZE) {
            val base = programCounter * INSTRUCTION_SIZE
            val operand = code[base + 1]
            when (code[base]) {
                MATCH_UNIT -> result = units[operand].match(context, adapter, options)
                NOT -> result = if (result == null) context.current else null
                AND_ENTER -> if (result == null) {
                    programCounter = code[base + 2]
                    continue
                }

                OR_ENTER -> if (result != null) {
                    programCounter = code[base + 2]
                    continue
                }

                AND_EXIT, OR_EXIT -> Unit
                else -> error("Unknown selector instruction")
            }
            programCounter++
        }
        return result
    }

    fun <T : Any> matchWithTrace(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        options: MatchOptions,
    ): SelectorMatch<T>? {
        val leftResults = mutableListOf<SelectorMatch<T>>()
        var programCounter = 0
        var result: SelectorMatch<T>? = null
        while (programCounter < code.size / INSTRUCTION_SIZE) {
            val base = programCounter * INSTRUCTION_SIZE
            val operand = code[base + 1]
            when (code[base]) {
                MATCH_UNIT -> result = units[operand]
                    .matchWithTrace(context, adapter, options)
                    ?.let { unit -> SelectorMatch(unit.target, arrayOf(unit)) }

                NOT -> result = if (result == null) {
                    SelectorMatch(context.current, emptyArray())
                } else {
                    null
                }

                AND_ENTER -> {
                    if (result == null) {
                        programCounter = code[base + 2]
                        continue
                    }
                    leftResults.add(result)
                }

                AND_EXIT -> {
                    val left = leftResults.removeAt(leftResults.lastIndex)
                    if (result != null) {
                        result = SelectorMatch(result.target, left.units + result.units)
                    }
                }

                OR_ENTER -> {
                    if (result != null) {
                        programCounter = code[base + 2]
                        continue
                    }
                }

                OR_EXIT -> Unit

                else -> error("Unknown selector instruction")
            }
            programCounter++
        }
        return result
    }

    fun isSlow(options: MatchOptions): Boolean {
        if ((!options.fastQuery || fastQueryList.isEmpty()) && !isMatchRoot) return true
        return units.any { it.hasSlowTraversal(options) }
    }

    fun validateType(globalType: SelectorType): TypeCheckFailure? {
        val collector = TypeCheckCollector(globalType, 1)
        collectTypeFailures(collector)
        return collector.failures.firstOrNull()
    }

    fun collectTypeFailures(globalType: SelectorType): List<TypeCheckFailure> {
        val collector = TypeCheckCollector(globalType, Int.MAX_VALUE)
        collectTypeFailures(collector)
        return collector.failures
    }

    private fun collectTypeFailures(collector: TypeCheckCollector) {
        units.forEach { unit ->
            unit.collectTypeFailures(collector)
            if (collector.isFull) return
        }
    }

    companion object {
        fun compile(
            expression: SelectorExpression,
            sourceMap: SelectorSourceMap?,
        ): SelectorProgram {
            val compiler = ProgramCompiler(expression, sourceMap)
            val metadata = analyzeSelector(expression)
            return SelectorProgram(
                code = compiler.code,
                units = compiler.units,
                fastQueryList = metadata.fastQueryList.orEmpty(),
                isMatchRoot = metadata.isMatchRoot,
            )
        }
    }
}

private class Label {
    val patches = mutableListOf<Int>()
}

private sealed interface CompileAction

private class CompileExpression(val expression: SelectorExpression) : CompileAction

private class Emit(
    val opcode: Int,
    val operand: Int,
    val target: Label? = null,
) : CompileAction

private class Mark(val label: Label) : CompileAction

private class ProgramCompiler(
    root: SelectorExpression,
    private val sourceMap: SelectorSourceMap?,
) {
    private val mutableCode = mutableListOf<Int>()
    private val mutableUnits = mutableListOf<CompiledUnitSelector>()

    val code: IntArray
    val units: List<CompiledUnitSelector>

    init {
        val actions = mutableListOf<CompileAction>(CompileExpression(root))
        while (actions.isNotEmpty()) {
            when (val action = actions.removeAt(actions.lastIndex)) {
                is Mark -> {
                    val address = mutableCode.size / INSTRUCTION_SIZE
                    action.label.patches.forEach { mutableCode[it] = address }
                }

                is Emit -> emit(action)
                is CompileExpression -> when (val expression = action.expression) {
                    is UnitSelectorExpression -> {
                        val operand = mutableUnits.size
                        mutableUnits.add(
                            CompiledUnitSelector(
                                expression = expression,
                                sourceMap = sourceMap,
                            ),
                        )
                        emit(Emit(MATCH_UNIT, operand))
                    }

                    is NotSelectorExpression -> {
                        actions.add(Emit(NOT, -1))
                        actions.add(CompileExpression(expression.expression))
                    }

                    is LogicalSelectorExpression -> {
                        val end = Label()
                        val isAnd = expression.operator == LogicalOperator.And
                        actions.add(Mark(end))
                        actions.add(Emit(if (isAnd) AND_EXIT else OR_EXIT, -1))
                        actions.add(CompileExpression(expression.right))
                        actions.add(
                            Emit(
                                opcode = if (isAnd) AND_ENTER else OR_ENTER,
                                operand = -1,
                                target = end,
                            ),
                        )
                        actions.add(CompileExpression(expression.left))
                    }
                }
            }
        }
        code = mutableCode.toIntArray()
        units = mutableUnits
    }

    private fun emit(instruction: Emit) {
        mutableCode.add(instruction.opcode)
        mutableCode.add(instruction.operand)
        mutableCode.add(-1)
        instruction.target?.patches?.add(mutableCode.lastIndex)
    }
}

private class SelectorMetadata(
    val fastQueryList: List<FastQuery>?,
    val isMatchRoot: Boolean,
)

private sealed interface MetadataAction

private class ReadMetadata(val expression: SelectorExpression) : MetadataAction

private class CombineMetadata(val operator: LogicalOperator) : MetadataAction

private fun analyzeSelector(expression: SelectorExpression): SelectorMetadata {
    val actions = mutableListOf<MetadataAction>(ReadMetadata(expression))
    val values = mutableListOf<SelectorMetadata>()
    while (actions.isNotEmpty()) {
        when (val action = actions.removeAt(actions.lastIndex)) {
            is CombineMetadata -> {
                val right = values.removeAt(values.lastIndex)
                val left = values.removeAt(values.lastIndex)
                values.add(
                    SelectorMetadata(
                        fastQueryList = when (action.operator) {
                            LogicalOperator.And -> when {
                                left.fastQueryList == null -> right.fastQueryList
                                right.fastQueryList == null -> left.fastQueryList
                                else -> (left.fastQueryList + right.fastQueryList).distinct()
                            }

                            LogicalOperator.Or -> if (
                                left.fastQueryList == null || right.fastQueryList == null
                            ) {
                                null
                            } else {
                                (left.fastQueryList + right.fastQueryList).distinct()
                            }
                        },
                        isMatchRoot = when (action.operator) {
                            LogicalOperator.And -> left.isMatchRoot || right.isMatchRoot
                            LogicalOperator.Or -> left.isMatchRoot && right.isMatchRoot
                        },
                    ),
                )
            }

            is ReadMetadata -> when (val current = action.expression) {
                is UnitSelectorExpression -> values.add(
                    SelectorMetadata(
                        fastQueryList = current.propertySelectors.last().fastQueryList,
                        isMatchRoot = current.propertySelectors.last().isMatchRoot,
                    ),
                )

                is NotSelectorExpression -> values.add(SelectorMetadata(null, false))
                is LogicalSelectorExpression -> {
                    actions.add(CombineMetadata(current.operator))
                    actions.add(ReadMetadata(current.right))
                    actions.add(ReadMetadata(current.left))
                }
            }
        }
    }
    return values.single()
}
