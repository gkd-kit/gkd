package li.gkd.selector

import li.gkd.selector.syntax.SelectorTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectorTokenizerTest {
    @Test
    fun tolerantTokenizerCoversEverySourcePosition() {
        val sources = listOf(
            "  @TextView[text=`a\\n` || index>=2] > Button  ",
            "TextView[text='unfinished",
            """TextView[text='\'""",
            "A ??? B",
            "😀[text='值']",
        )

        sources.forEach { source ->
            val tokens = SelectorTokenizer.tokenize(source)
            assertEquals(source, tokens.joinToString("") { source.substring(it.start, it.end) })
            tokens.asList().zipWithNext().forEach { (left, right) ->
                assertEquals(left.end, right.start)
            }
            if (source.isNotEmpty()) {
                assertEquals(0, tokens.first().start)
                assertEquals(source.length, tokens.last().end)
            }
        }
    }

    @Test
    fun completeSelectorUsesStableTokenKinds() {
        val source = "@TextView[text~='a'&&index>=2] +(3n-1) *"
        val actual = SelectorTokenizer.tokenize(source).map {
            source.substring(it.start, it.end) to it.kind
        }

        assertEquals(
            listOf(
                "@" to SelectorTokenKind.Target,
                "TextView" to SelectorTokenKind.Selector,
                "[" to SelectorTokenKind.Punctuation,
                "text" to SelectorTokenKind.Identifier,
                "~=" to SelectorTokenKind.CompareOperator,
                "'a'" to SelectorTokenKind.String,
                "&&" to SelectorTokenKind.LogicalOperator,
                "index" to SelectorTokenKind.Identifier,
                ">=" to SelectorTokenKind.CompareOperator,
                "2" to SelectorTokenKind.Integer,
                "]" to SelectorTokenKind.Punctuation,
                " " to SelectorTokenKind.Whitespace,
                "+" to SelectorTokenKind.RelationOperator,
                "(" to SelectorTokenKind.Punctuation,
                "3" to SelectorTokenKind.Integer,
                "n" to SelectorTokenKind.PolynomialVariable,
                "-" to SelectorTokenKind.ArithmeticOperator,
                "1" to SelectorTokenKind.Integer,
                ")" to SelectorTokenKind.Punctuation,
                " " to SelectorTokenKind.Whitespace,
                "*" to SelectorTokenKind.Wildcard,
            ),
            actual,
        )
        assertTrue(SelectorTokenizer.tokenize("[x=١]").any { it.kind == SelectorTokenKind.Invalid })
        assertEquals(
            SelectorTokenKind.Invalid,
            SelectorTokenizer.tokenize("[text='unfinished").last().kind,
        )
    }

    @Test
    fun operatorTokensUseLongestMatchAndCorrectScope() {
        val cases = listOf(
            OperatorTokenCase("[a=1]", "=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a!=1]", "!=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a>1]", ">", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a<1]", "<", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a>=1]", ">=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a<=1]", "<=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a^='x']", "^=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a!^='x']", "!^=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a*='x']", "*=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a!*='x']", "!*=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a$='x']", "$=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a!$='x']", "!$=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a~='x']", "~=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a!~='x']", "!~=", SelectorTokenKind.CompareOperator, SelectorTokenScope.Property),
            OperatorTokenCase("A + B", "+", SelectorTokenKind.RelationOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("A - B", "-", SelectorTokenKind.RelationOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("A > B", ">", SelectorTokenKind.RelationOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("A < B", "<", SelectorTokenKind.RelationOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("A << B", "<<", SelectorTokenKind.RelationOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("A -> B", "->", SelectorTokenKind.RelationOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("(A) && (B)", "&&", SelectorTokenKind.LogicalOperator, SelectorTokenScope.Selector),
            OperatorTokenCase("(A) || (B)", "||", SelectorTokenKind.LogicalOperator, SelectorTokenScope.Selector),
            OperatorTokenCase("!(A)", "!", SelectorTokenKind.LogicalOperator, SelectorTokenScope.Selector),
            OperatorTokenCase("[a=1&&b=2]", "&&", SelectorTokenKind.LogicalOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[a=1||b=2]", "||", SelectorTokenKind.LogicalOperator, SelectorTokenScope.Property),
            OperatorTokenCase("[!(a=1)]", "!", SelectorTokenKind.LogicalOperator, SelectorTokenScope.Property),
            OperatorTokenCase("A +(2n+1) B", "+", SelectorTokenKind.ArithmeticOperator, SelectorTokenScope.Relation, 1),
            OperatorTokenCase("A +(2n-1) B", "-", SelectorTokenKind.ArithmeticOperator, SelectorTokenScope.Relation),
            OperatorTokenCase("[a=-1]", "-1", SelectorTokenKind.Integer, SelectorTokenScope.Property),
            OperatorTokenCase("[a~1]", "~", SelectorTokenKind.Invalid, SelectorTokenScope.Property),
            OperatorTokenCase("A ? B", "?", SelectorTokenKind.Invalid, SelectorTokenScope.Selector),
        )

        cases.forEach { case ->
            val tokens = SelectorTokenizer.tokenize(case.source).filter {
                case.source.substring(it.start, it.end) == case.text
            }
            val token = tokens[case.occurrence]
            assertEquals(case.kind, token.kind, case.source)
            assertEquals(case.scope, token.scope, case.source)
            assertEquals(case.text, case.source.substring(token.start, token.end), case.source)
        }
    }

    @Test
    fun tokenScopesDifferentiateSelectorPropertyAndRelationSyntax() {
        val source = "A[index=2 || count=3] +( 4n - 1 ) B || C"
        val tokens = SelectorTokenizer.tokenize(source)

        fun scopes(text: String): List<SelectorTokenScope> = tokens
            .filter { source.substring(it.start, it.end) == text }
            .map { it.scope }

        assertEquals(listOf(SelectorTokenScope.Selector), scopes("A"))
        assertEquals(listOf(SelectorTokenScope.Property), scopes("index"))
        assertEquals(listOf(SelectorTokenScope.Property), scopes("2"))
        assertEquals(
            listOf(SelectorTokenScope.Property, SelectorTokenScope.Selector),
            scopes("||"),
        )
        assertEquals(listOf(SelectorTokenScope.Relation), scopes("+"))
        assertEquals(listOf(SelectorTokenScope.Relation), scopes("4"))
        assertEquals(listOf(SelectorTokenScope.Relation), scopes("n"))
        assertEquals(listOf(SelectorTokenScope.Relation), scopes("-"))
        assertTrue(
            tokens.any {
                it.kind == SelectorTokenKind.Whitespace &&
                        it.scope == SelectorTokenScope.Relation
            },
        )
        assertEquals(
            listOf(SelectorTokenScope.Property, SelectorTokenScope.Property),
            scopes("[") + scopes("]"),
        )
    }

    private data class OperatorTokenCase(
        val source: String,
        val text: String,
        val kind: SelectorTokenKind,
        val scope: SelectorTokenScope,
        val occurrence: Int = 0,
    )
}
