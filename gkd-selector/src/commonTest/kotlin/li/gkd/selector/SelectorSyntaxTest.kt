package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class SelectorSyntaxTest {
    @Test
    fun grammarMatrixCompilesAndCanonicalOutputRoundTrips() {
        val sources = listOf(
            "A",
            "  A\t",
            "@A",
            "@*",
            "[x=1]",
            "android.widget.TextView",
            "A[null=null][false=false][true=true][x=-2147483648][x=2147483647]",
            "A[x='a'][x=\"a\"][x=`a`]",
            "A[x='\\\\\\\'\\\"\\`\\n\\r\\t\\b\\x0f\\u4e2d']",
            "A[abc_123=null][nullValue=true][trueValue=false]",
            "A[a.b.c=null]",
            "A[a()=null]",
            "A[a(b,c,1,-2,true,false,null,'x')=null]",
            "A[x=(y)]",
            "A[x=((y))]",
            "A[x=(a.b(c))]",
            "A[equal((x),('x'))=true]",
            "A[a.b(c,d).e(f).g(1,2,true)=null]",
            "A[a . b ( 1 , c.d() ) =null]",
            "A[a=1||b=2&&c=3]",
            "A[(a=1||b=2)&&c=3]",
            "A[!(a=1||b=2)]",
            "A[a=1][b=2][c=3]",
            "A + B",
            "A - B",
            "A > B",
            "A < B",
            "A << B",
            "A -> B",
            "A +(1) B",
            "A +(1,2,10) B",
            "A +n B",
            "A +2n B",
            "A +(n+1) B",
            "A +(2n-1) B",
            "A +(7+9n) B",
            "A +(-n+4) B",
            "A +(-3n+10) B",
            "A +(99-n) B",
            "A +(+3) B",
            "A +3 B",
            "A B",
            "A\n>\tB",
            "(A)",
            "!(A)",
            "(A) || (B)",
            "(A) && (B)",
            "(A) || (B) && !(C)",
        )

        sources.forEach { source ->
            val compiled = assertIs<SelectorCompileResult.Success>(Selector.compile(source), source)
            val canonical = compiled.value.toString()
            val roundTrip = assertIs<SelectorCompileResult.Success>(
                Selector.compile(canonical),
                "$source -> $canonical",
            )
            assertEquals(canonical, roundTrip.value.toString(), source)
            assertEquals(canonical, Selector.parse(source).value.toString(), source)
        }
    }

    @Test
    fun stringifyPreservesEmojiAndEscapesLoneSurrogates() {
        val emoji = "\uD83D\uDE00"
        listOf(
            "[x='$emoji']",
            "[x='\\uD83D\\uDE00']",
        ).forEach { source ->
            val canonical = Selector.compile(source).value.toString()
            assertEquals("[x=\"$emoji\"]", canonical, source)
            assertEquals(canonical, Selector.compile(canonical).value.toString(), source)
        }

        listOf(
            0xD83D to "\\ud83d",
            0xDE00 to "\\ude00",
        ).forEach { (code, escaped) ->
            val surrogate = buildString { append(code.toChar()) }
            val source = "[x='$surrogate']"
            val canonical = Selector.compile(source).value.toString()
            assertEquals("[x=\"$escaped\"]", canonical)
            assertEquals(canonical, Selector.compile(canonical).value.toString())
        }
    }

    @Test
    fun invalidGrammarMatrixFailsInCompileAndFullParse() {
        val sources = listOf(
            "",
            " \t\n",
            "@",
            ".A",
            "A.",
            "A..B",
            "A[]",
            "A[x]",
            "A[x=]",
            "A[=1]",
            "A[x==1]",
            "A[x=+1]",
            "A[x=01]",
            "A[x=-01]",
            "A[x=2147483648]",
            "A[x=-2147483649]",
            "A[x='unterminated]",
            "A[x='\\q']",
            "A[x='line\nbreak']",
            "A[x~=1]",
            "A[x~=pattern]",
            "A[x~='(']",
            "A[x.1=true]",
            "A[x.null=true]",
            "A[x.true=true]",
            "A[x()()=true]",
            "A[x=(y).length]",
            "A[equal((x)(1),'x')=true]",
            "A[x(,1)=true]",
            "A[x(1,)=true]",
            "A[x(1 2)=true]",
            "A[!!(x=true)]",
            "A[! (x=true)]",
            "A[x=1&&]",
            "A[&&x=1]",
            "A[x=1|| ||y=2]",
            "A>B",
            "A >B",
            "A> B",
            "A +",
            "A + B +",
            "A +(0) B",
            "A +(-1) B",
            "A +(1,) B",
            "A +(0,1) B",
            "A +(2,1) B",
            "A +(1,1) B",
            "A +(1,2,2) B",
            "A +(n+n) B",
            "A +(n-2n) B",
            "A +(1+2) B",
            "A +(n+1+2) B",
            "A +(0n) B",
            "A +(-n+1) B",
            "!A",
            "! (A)",
            "A || (B)",
            "(A) || B",
            "(A) ||",
            "|| (A)",
            "((A)",
            "(A))",
        )

        sources.forEach { source ->
            val compileResult = assertIs<SelectorCompileResult.Failure>(Selector.compile(source), source)
            val parseResult = assertIs<SelectorParseResult.Failure>(Selector.parse(source), source)
            assertEquals(compileResult.error.index, parseResult.error.index, source)
            assertEquals(source.length, parseResult.tokens.sumOf { it.end - it.start }, source)
        }
    }

    @Test
    fun representativeSyntaxErrorsExposeExactIndexes() {
        val cases = listOf(
            ErrorCase("", 0),
            ErrorCase("A[", 2),
            ErrorCase("A[x=]", 4),
            ErrorCase("A >B", 3),
            ErrorCase("A> B", 1),
            ErrorCase("A +(1,) B", 6),
            ErrorCase("A || (B)", 2),
            ErrorCase("(A) || B", 7),
            ErrorCase("[a.1=true]", 3),
            ErrorCase("[a()()=true]", 4),
            ErrorCase("[a=01]", 3),
            ErrorCase("[a=2147483648]", 3),
            ErrorCase("[a='\\q']", 5),
            ErrorCase("[a~='(']", 4),
        )

        cases.forEach { case ->
            val result = assertIs<SelectorCompileResult.Failure>(Selector.compile(case.source))
            assertEquals(case.index, result.error.index, case.source)
        }
    }

    @Test
    fun parenthesizedValuesAreTransparentAndDoNotUseParserRecursion() {
        val equivalentSources = listOf(
            "View[text=(desc)]" to "View[text=desc]",
            "View[text=((desc))]" to "View[text=desc]",
            "View[text=( parent.current.text )]" to "View[text=parent.current.text]",
            "View[equal((text),('x'))=true]" to "View[equal(text,'x')=true]",
            "View[text.substring((0),(1))=('x')]" to
                    "View[text.substring(0,1)='x']",
        )
        equivalentSources.forEach { (grouped, plain) ->
            val expected = Selector.compile(plain).value.toString()
            assertEquals(expected, Selector.compile(grouped).value.toString(), grouped)
            assertEquals(expected, Selector.parse(grouped).value.toString(), grouped)
        }

        val depth = 5_000
        val source = "View[text=" + "(".repeat(depth) + "desc" + ")".repeat(depth) + "]"
        assertEquals("View[text=desc]", Selector.compile(source).value.toString())
        assertEquals("View[text=desc]", Selector.parse(source).value.toString())
    }

    @Test
    fun malformedStringsExposeExactErrorAndInvalidTokenRanges() {
        val cases = listOf(
            ExactErrorCase("[x='\\x']", 6, 7, "hex digit"),
            ExactErrorCase("[x='\\x0']", 7, 8, "hex digit"),
            ExactErrorCase("[x='\\x0g']", 7, 8, "hex digit"),
            ExactErrorCase("[x='\\u']", 6, 7, "hex digit"),
            ExactErrorCase("[x='\\u123']", 9, 10, "hex digit"),
            ExactErrorCase("[x='\\u12g4']", 8, 9, "hex digit"),
            ExactErrorCase("[x='a\rb']", 5, 6, "escaped control character"),
            ExactErrorCase("[x='a\tb']", 5, 6, "escaped control character"),
            ExactErrorCase("[x='a\u0000b']", 5, 6, "escaped control character"),
        )

        cases.forEach { case ->
            val failure = assertIs<SelectorCompileResult.Failure>(Selector.compile(case.source), case.source)
            assertEquals(case.expected, failure.error.expected, case.source)
            assertEquals(SourceRange(case.start, case.end), failure.error.range, case.source)

            val invalid = Selector.tokenize(case.source).first { it.kind == SelectorTokenKind.Invalid }
            assertEquals(case.source.indexOf('\''), invalid.start, case.source)
            assertEquals(case.end, invalid.end, case.source)
        }
    }

    @Test
    fun delimiterAndNumericErrorsExposeExactRanges() {
        val cases = listOf(
            ExactErrorCase("A[x=1", 5, 5, "']'"),
            ExactErrorCase("A[x=()]", 5, 6, "value"),
            ExactErrorCase("A[x=(y]", 6, 7, "')'"),
            ExactErrorCase("A[x.a(1,)=2]", 8, 9, "value after comma"),
            ExactErrorCase("((A)", 4, 4, "')'"),
            ExactErrorCase("A + B +", 7, 7, "whitespace after relation expression"),
            ExactErrorCase("A[x=1&&]", 7, 8, "value"),
            ExactErrorCase("A +(2147483648) B", 4, 5, "32-bit integer"),
            ExactErrorCase("A +(1,1) B", 6, 7, "increasing integer"),
            ExactErrorCase("A >B", 3, 4, "whitespace after relation expression"),
            ExactErrorCase("A> B", 1, 2, "end of selector"),
        )

        cases.forEach { case ->
            val failure = assertIs<SelectorCompileResult.Failure>(Selector.compile(case.source), case.source)
            assertEquals(case.expected, failure.error.expected, case.source)
            assertEquals(SourceRange(case.start, case.end), failure.error.range, case.source)
        }
    }

    @Test
    fun documentedSelectorsCompile() {
        val sources = listOf(
            "*",
            "TextView",
            "@TextView[a=1][b^='2'][c*='a'||d.length>7&&e=false][!(f=true)][g.plus(1)>0]",
            "A + B",
            "A -(1,2,3) B",
            "A +(3n+1) B",
            "A <<2 B",
            "N ->1 B > A",
            "(A + B) || (M > N)",
            "(A + B) && (M > N)",
            "!(A + B)",
            "@LinearLayout > TextView[id=`com.byted.pangle:id/tt_item_tv`][text=`不感兴趣`]",
            "TextView[text=\"\\\\\\n\"]",
            "TextView[text~=`(?is).*abc.*`]",
            "[null=parent]",
            "[parent=null]",
            "View[a.b(c,d).e(f).g(1,2,true)=null]",
            "View[a>1&&b>1&&c>1||d>1&&x^='1']",
            "@View[a=1][b!=2][c>=3][d<=4][e^='x'][f!^='x'][g*='x'][h!*='x'][i$='x'][j!$='x'][k~='x'][l!~='x']",
            "A +(3n) B",
            "A +3n B",
            "A +(+3) B",
            "A +(2n-1) B",
            "A +(-n+4) B",
            "A >(1n+0) B",
            "A >n B",
            "A B",
            "N ->(1,2) B > A",
            "View[a()=null][a.b.c='x']",
            "A +(1) B",
        )

        sources.forEach { source ->
            val result = Selector.compile(source)
            assertIs<SelectorCompileResult.Success>(result, source)
        }
    }

    @Test
    fun documentedInvalidSelectorsFail() {
        val sources = listOf(
            "div>img",
            "[!!(a=true)]",
            "[a.1=true]",
            "[a.null=true]",
            "[a.true=true]",
            "[a()()=true]",
            "[a=+1]",
            "A || (B)",
            "(A) || B",
            "[a=01]",
            "[a=-01]",
            "View[a(1,)=2]",
            "A +(2,1) B",
            "A +(0) B",
            "A +(1,) B",
            "A +(2,2) B",
            "View[text~=1]",
            "View[text~=pattern]",
        )

        sources.forEach { source ->
            assertIs<SelectorCompileResult.Failure>(Selector.compile(source), source)
        }
    }

    @Test
    fun logicalPrecedenceMatchesDocumentation() {
        val actual = Selector.compile("[a>1||b>1&&c>1||d>1]").value.toString()
        val expected = Selector.compile("[a>1||(b>1&&c>1)||d>1]").value.toString()
        assertEquals(expected, actual)

        val selectorActual = Selector.compile("(A) || (B) && (C)").value.toString()
        val selectorExpected = Selector.compile("(A) || ((B) && (C))").value.toString()
        assertEquals(selectorExpected, selectorActual)
    }

    @Test
    fun syntaxComponentsComposeAndRoundTrip() {
        val leftSelectors = listOf(
            "A",
            "@A[x=1]",
            "A[x.a(1)=null&&flag=true]",
        )
        val relations = listOf(
            " ",
            " + ",
            " >(1,2) ",
            " +(2n-1) ",
        )
        val rightSelectors = listOf(
            "B",
            "B[text^='x']",
            "*[count>=2]",
        )
        val units = leftSelectors.flatMap { left ->
            relations.flatMap { relation ->
                rightSelectors.map { right -> left + relation + right }
            }
        }
        val sources = buildList {
            addAll(units)
            units.forEach { unit ->
                add("($unit) || (C[enabled=true])")
                add("!($unit)")
            }
        }

        sources.forEach { source ->
            val compiled = assertIs<SelectorCompileResult.Success>(Selector.compile(source), source)
            val canonical = compiled.value.toString()
            val parsed = assertIs<SelectorParseResult.Success>(Selector.parse(source), source)
            assertEquals(canonical, parsed.value.toString(), source)
            assertEquals(canonical, Selector.compile(canonical).value.toString(), source)
            assertEquals(source, parsed.tokens.joinToString("") { source.substring(it.start, it.end) }, source)
            parsed.tokens.asList().zipWithNext().forEach { (left, right) ->
                assertEquals(left.end, right.start, source)
            }
            val tokenBoundaries = buildSet {
                parsed.tokens.forEach { token ->
                    add(token.start)
                    add(token.end)
                }
            }
            parsed.positions.forEach { position ->
                assertTrue(position.start >= 0, "$source: $position")
                assertTrue(position.end in (position.start + 1)..source.length, "$source: $position")
                assertTrue(position.start in tokenBoundaries, "$source: $position")
                assertTrue(position.end in tokenBoundaries, "$source: $position")
            }
        }
    }

    @Test
    fun failureExposesExactErrorWithoutCatch() {
        val source = "TextView[text=]"
        val result = assertIs<SelectorCompileResult.Failure>(Selector.compile(source))
        assertEquals("value", result.error.expected)
        assertEquals("]", result.error.actual)
        assertEquals(source.indexOf(']'), result.error.index)
        assertEquals(
            SourceRange(source.indexOf(']'), source.indexOf(']') + 1),
            result.error.range,
        )

        try {
            result.value
            fail("Failure.value must throw")
        } catch (error: SelectorSyntaxException) {
            assertSame(result.error, error)
        }

        val eofSource = "TextView[text='x'"
        val eofResult = assertIs<SelectorCompileResult.Failure>(Selector.compile(eofSource))
        assertEquals("EOF", eofResult.error.actual)
        assertEquals(eofSource.length, eofResult.error.index)
        assertEquals(SourceRange(eofSource.length, eofSource.length), eofResult.error.range)

        val parseResult = assertIs<SelectorParseResult.Failure>(Selector.parse(source))
        assertEquals(result.error.index, parseResult.error.index)
        try {
            parseResult.value
            fail("Failure.value must throw")
        } catch (error: SelectorSyntaxException) {
            assertSame(parseResult.error, error)
        }
    }

    @Test
    fun invalidRegexRetainsEngineDiagnosticAndLiteralRange() {
        val source = "[a~='(']"
        val failure = assertIs<SelectorCompileResult.Failure>(Selector.compile(source))
        val literalStart = source.indexOf('\'')

        assertEquals("valid regular expression string", failure.error.expected)
        assertEquals(SourceRange(literalStart, literalStart + 3), failure.error.range)
        val detail = failure.error.detail
        assertTrue(detail?.isNotBlank() == true)
        assertTrue(failure.error.message.contains(detail))
    }

    @Test
    fun deeplyNestedSyntaxUsesExplicitStacks() {
        val selectorSource = "(".repeat(5_000) + "A" + ")".repeat(5_000)
        assertIs<SelectorCompileResult.Success>(Selector.compile(selectorSource))

        val propertySource =
            "View[" + "(".repeat(5_000) + "a=1" + ")".repeat(5_000) + "]"
        assertIs<SelectorCompileResult.Success>(Selector.compile(propertySource))

        val callSource = "View[x=" + "a(".repeat(2_000) + "1" + ")".repeat(2_000) + "]"
        assertIs<SelectorCompileResult.Success>(Selector.compile(callSource))
    }

    @Test
    fun longLogicalChainUsesIterativeReduction() {
        val source = List(500) { "(A)" }.joinToString(" || ")
        assertIs<SelectorCompileResult.Success>(Selector.compile(source))
    }

    private data class ErrorCase(val source: String, val index: Int)

    private data class ExactErrorCase(
        val source: String,
        val start: Int,
        val end: Int,
        val expected: String,
    )
}
