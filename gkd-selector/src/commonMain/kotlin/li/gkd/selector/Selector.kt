package li.gkd.selector

import li.gkd.selector.engine.SelectorExpression
import li.gkd.selector.engine.SelectorProgram
import li.gkd.selector.syntax.PositionRecorder
import li.gkd.selector.syntax.SelectorParser
import li.gkd.selector.syntax.SelectorPrinter
import li.gkd.selector.syntax.SelectorTokenizer
import kotlin.js.JsExport
import kotlin.js.JsStatic

@JsExport
public class Selector internal constructor(
    private val expression: SelectorExpression,
    private val sourceMap: SelectorSourceMap?,
) {
    private val program = SelectorProgram.compile(expression, sourceMap)

    override fun toString(): String {
        return SelectorPrinter.render(expression)
    }

    @JsExport.Ignore
    public fun <T : Any> matchWithTrace(
        node: T,
        adapter: NodeAdapter<T>,
        options: MatchOptions = MatchOptions.default,
    ): SelectorMatch<T>? {
        return program.matchWithTrace(
            context = MatchContext(node),
            adapter = adapter,
            options = options,
        )
    }

    @JsExport.Ignore
    public fun <T : Any> match(
        node: T,
        adapter: NodeAdapter<T>,
        options: MatchOptions = MatchOptions.default,
    ): T? {
        return program.match(MatchContext(node), adapter, options)
    }

    internal val fastQueryList: List<FastQuery>
        get() = program.fastQueryList

    public val isMatchRoot: Boolean
        get() = program.isMatchRoot

    public fun isSlow(options: MatchOptions): Boolean = program.isSlow(options)

    public fun validateType(typeModel: SelectorTypeModel): SelectorTypeResult {
        val failure = program.validateType(typeModel.globalType)
            ?: return SelectorTypeResult.Success(this)
        return SelectorTypeResult.Failure(
            failure.toException(sourceMap?.rangeOf(failure.positionValue)),
        )
    }

    /** Collects every independent type error, retaining ranges when this selector was parsed. */
    public fun getTypeErrors(typeModel: SelectorTypeModel): Array<out SelectorTypeException> =
        program.collectTypeFailures(typeModel.globalType)
            .mapIndexed { order, failure ->
                order to failure.toException(sourceMap?.rangeOf(failure.positionValue))
            }
            .sortedWith(
                compareBy<Pair<Int, SelectorTypeException>> { (_, error) ->
                    error.index ?: Int.MAX_VALUE
                }.thenBy { (order) -> order },
            )
            .map { (_, error) -> error }
            .toTypedArray()

    public companion object {
        /** Strict semantic compilation for matching-only callers. */
        @JsStatic
        public fun compile(source: String): SelectorCompileResult = try {
            SelectorCompileResult.Success(Selector(SelectorParser(source).readSelector(), null))
        } catch (error: SelectorSyntaxException) {
            SelectorCompileResult.Failure(error)
        }

        /** Strict semantic parsing with source positions and highlight tokens. */
        @JsStatic
        public fun parse(source: String): SelectorParseResult {
            val tokens = SelectorTokenizer.tokenize(source)
            val recorder = PositionRecorder()
            return try {
                val parsed = SelectorParser(source, recorder).readSelector()
                val recordedPositions = recorder.freeze()
                val selector = Selector(parsed, recordedPositions.sourceMap)
                SelectorParseResult.Success(
                    value = selector,
                    tokens = tokens,
                    positions = recordedPositions.positions,
                )
            } catch (error: SelectorSyntaxException) {
                SelectorParseResult.Failure(error, tokens, recorder.freezePositions())
            }
        }

        /** Tolerant lexical scanning for editors and syntax highlighters. */
        @JsStatic
        public fun tokenize(source: String): Array<out SelectorToken> =
            SelectorTokenizer.tokenize(source)
    }
}
