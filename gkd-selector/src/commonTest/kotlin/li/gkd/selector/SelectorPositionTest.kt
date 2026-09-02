package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SelectorPositionTest {
    @Test
    fun fullParseReturnsMatchableValueAndPositions() {
        val source = " \nTextView[text='hello'] > @Button[clickable=true]\t"
        val result = assertIs<SelectorParseResult.Success>(Selector.parse(source))

        assertEquals(Selector.compile(source).value.toString(), result.value.toString())
        val selectorPosition = result.positions.single {
            it.kind == SelectorPositionKind.Selector
        }
        assertEquals(SourceRange(0, source.length), selectorPosition.range)
        assertTrue(
            result.positions.any {
                it.kind == SelectorPositionKind.Unit
            },
        )

        val binaryPosition = result.positions.first {
            it.kind == SelectorPositionKind.Comparison &&
                    source.substring(it.start, it.end) == "text='hello'"
        }
        assertEquals("text='hello'", source.substring(binaryPosition.start, binaryPosition.end))

        val unitPosition = result.positions.first {
            it.kind == SelectorPositionKind.Unit
        }
        assertTrue(unitPosition.start < unitPosition.end)
    }

    @Test
    fun fullParseFailureKeepsTokensAndIndex() {
        val source = "TextView[text='hello'"
        val result = assertIs<SelectorParseResult.Failure>(Selector.parse(source))

        assertEquals(source.length, result.error.index)
        assertEquals(SourceRange(source.length, source.length), result.error.range)
        assertEquals(source, result.tokens.joinToString("") { source.substring(it.start, it.end) })
        assertEquals(
            listOf(
                PositionExpectation(SelectorPositionKind.Identifier, 9, 13, "text"),
                PositionExpectation(SelectorPositionKind.StringLiteral, 14, 21, "'hello'"),
                PositionExpectation(SelectorPositionKind.Comparison, 9, 21, "text='hello'"),
            ),
            result.positions.map { position ->
                PositionExpectation(
                    position.kind,
                    position.start,
                    position.end,
                    source.substring(position.start, position.end),
                )
            },
        )
    }

    @Test
    fun repeatedLiteralsHaveIndependentExactPositions() {
        val source = "[a=null||b=null]"
        val result = assertIs<SelectorParseResult.Success>(Selector.parse(source))
        val nullPositions = result.positions
            .filter { it.kind == SelectorPositionKind.NullLiteral }
            .sortedBy { it.start }

        assertEquals("null", source.substring(nullPositions[0].start, nullPositions[0].end))
        assertEquals("null", source.substring(nullPositions[1].start, nullPositions[1].end))
        assertTrue(nullPositions[0].start < nullPositions[1].start)
        assertEquals(
            2,
            result.positions.count { it.kind == SelectorPositionKind.NullLiteral },
        )
    }

    @Test
    fun regexLiteralPositionBelongsToTheMatchableAstNode() {
        val source = "[text~='a.*']"
        val result = assertIs<SelectorParseResult.Success>(Selector.parse(source))
        val range = result.positions.single { it.kind == SelectorPositionKind.StringLiteral }

        assertEquals("'a.*'", source.substring(range.start, range.end))
        assertEquals(
            1,
            result.positions.count { it.kind == SelectorPositionKind.StringLiteral },
        )
    }

    @Test
    fun tokenizerUsesTheSameAsciiIntegerGrammarAsTheParser() {
        val source = "[x=١]"
        val tokens = Selector.tokenize(source)

        assertEquals(
            SelectorTokenKind.Invalid,
            tokens.single { source.substring(it.start, it.end) == "١" }.kind,
        )
        val failure = assertIs<SelectorCompileResult.Failure>(Selector.compile(source))
        assertEquals(3, failure.error.index)
    }

    @Test
    fun fullParseRecordsEverySemanticLayerWithExactRanges() {
        val source = "!((@A[x.a(1,'s')=null&&!(flag=false)] +(1,2) B) || (C +(2n-1) D))"
        val result = assertIs<SelectorParseResult.Success>(Selector.parse(source))
        val expected = listOf(
            PositionExpectation(SelectorPositionKind.Identifier, 6, 7, "x"),
            PositionExpectation(SelectorPositionKind.MemberAccess, 6, 9, "x.a"),
            PositionExpectation(SelectorPositionKind.IntLiteral, 10, 11, "1"),
            PositionExpectation(SelectorPositionKind.StringLiteral, 12, 15, "'s'"),
            PositionExpectation(SelectorPositionKind.Call, 6, 16, "x.a(1,'s')"),
            PositionExpectation(SelectorPositionKind.NullLiteral, 17, 21, "null"),
            PositionExpectation(SelectorPositionKind.Comparison, 6, 21, "x.a(1,'s')=null"),
            PositionExpectation(SelectorPositionKind.Identifier, 25, 29, "flag"),
            PositionExpectation(SelectorPositionKind.BooleanLiteral, 30, 35, "false"),
            PositionExpectation(SelectorPositionKind.Comparison, 25, 35, "flag=false"),
            PositionExpectation(SelectorPositionKind.NegatedCondition, 23, 36, "!(flag=false)"),
            PositionExpectation(SelectorPositionKind.LogicalCondition, 6, 36, "x.a(1,'s')=null&&!(flag=false)"),
            PositionExpectation(SelectorPositionKind.Property, 5, 37, "[x.a(1,'s')=null&&!(flag=false)]"),
            PositionExpectation(SelectorPositionKind.PropertySelector, 3, 37, "@A[x.a(1,'s')=null&&!(flag=false)]"),
            PositionExpectation(SelectorPositionKind.TupleRange, 39, 44, "(1,2)"),
            PositionExpectation(SelectorPositionKind.Relation, 38, 44, "+(1,2)"),
            PositionExpectation(SelectorPositionKind.PropertySelector, 45, 46, "B"),
            PositionExpectation(SelectorPositionKind.Unit, 3, 46, "@A[x.a(1,'s')=null&&!(flag=false)] +(1,2) B"),
            PositionExpectation(SelectorPositionKind.PropertySelector, 52, 53, "C"),
            PositionExpectation(SelectorPositionKind.PolynomialRange, 55, 61, "(2n-1)"),
            PositionExpectation(SelectorPositionKind.Relation, 54, 61, "+(2n-1)"),
            PositionExpectation(SelectorPositionKind.PropertySelector, 62, 63, "D"),
            PositionExpectation(SelectorPositionKind.Unit, 52, 63, "C +(2n-1) D"),
            PositionExpectation(
                SelectorPositionKind.LogicalSelector,
                2,
                64,
                "(@A[x.a(1,'s')=null&&!(flag=false)] +(1,2) B) || (C +(2n-1) D)",
            ),
            PositionExpectation(
                SelectorPositionKind.NegatedSelector,
                0,
                65,
                "!((@A[x.a(1,'s')=null&&!(flag=false)] +(1,2) B) || (C +(2n-1) D))",
            ),
            PositionExpectation(SelectorPositionKind.Selector, 0, 65, source),
        )

        val actual = result.positions.map { position ->
            PositionExpectation(
                position.kind,
                position.start,
                position.end,
                source.substring(position.start, position.end),
            )
        }
        assertEquals(expected.size, actual.size)
        assertEquals(expected.toSet(), actual.toSet())
    }

    @Test
    fun singleUnitRangeExcludesTopLevelPadding() {
        val source = " @Root > Button "
        val result = assertIs<SelectorParseResult.Success>(Selector.parse(source))

        val unit = result.positions.single { it.kind == SelectorPositionKind.Unit }
        val selector = result.positions.single { it.kind == SelectorPositionKind.Selector }
        assertEquals(SourceRange(1, 15), SourceRange(unit.start, unit.end))
        assertEquals("@Root > Button", source.substring(unit.start, unit.end))
        assertEquals(SourceRange(0, source.length), SourceRange(selector.start, selector.end))
    }

    @Test
    fun indexesUseUtf16CodeUnitsOnJvmAndJs() {
        val source = "A[text='😀']"
        val result = assertIs<SelectorParseResult.Success>(Selector.parse(source))
        val stringPosition = result.positions.single {
            it.kind == SelectorPositionKind.StringLiteral
        }

        assertEquals(source.indexOf('\''), stringPosition.start)
        assertEquals(source.lastIndexOf('\'') + 1, stringPosition.end)
        assertEquals("'😀'", source.substring(stringPosition.start, stringPosition.end))
    }

    @Test
    fun semanticPositionBoundariesNeverSplitHighlightTokens() {
        val sources = listOf(
            "!((@A[x.a(1,'s')=null&&!(flag=false)] +(1,2) B) || (C +(2n-1) D))",
            "TextView[text='unfinished",
        )

        sources.forEach { source ->
            val result = Selector.parse(source)
            val boundaries = buildSet {
                result.tokens.forEach { token ->
                    add(token.start)
                    add(token.end)
                }
            }
            result.positions.forEach { position ->
                assertTrue(position.start in boundaries, position.toString())
                assertTrue(position.end in boundaries, position.toString())
                result.tokens.forEach { token ->
                    val overlaps = token.start < position.end && position.start < token.end
                    if (overlaps) {
                        assertTrue(
                            token.start >= position.start && token.end <= position.end,
                            "Token $token crosses $position",
                        )
                    }
                }
            }
        }
    }

    private data class PositionExpectation(
        val kind: SelectorPositionKind,
        val start: Int,
        val end: Int,
        val text: String,
    )
}

private val SelectorPosition.range: SourceRange
    get() = SourceRange(start, end)
