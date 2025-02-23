package li.songe.selector

import junit.framework.TestCase.assertTrue
import li.songe.selector.connect.ConnectOperator
import li.songe.selector.connect.ConnectSegment
import li.songe.selector.connect.PolynomialExpression
import li.songe.selector.connect.TupleExpression
import li.songe.selector.parser.SelectorParser
import kotlin.test.Test

class ParserUnitTest {

    @Test
    fun value() {
        assert(SelectorParser("null").readValueExpression().value == null)
        assert(SelectorParser("true").readValueExpression().value == true)
        assert(SelectorParser("false").readValueExpression().value == false)
        assert(SelectorParser("123").readValueExpression().value == 123)
        assert(SelectorParser("-123").readValueExpression().value == -123)
        assert(SelectorParser("``").readValueExpression().value == "")
        assert(SelectorParser("'abc\\\"'").readValueExpression().value == "abc\"")
        assert(SelectorParser("abc.xyz").readValueExpression().value == "abc.xyz")
        SelectorParser("abc.xyz .m(null, false,a,\n\t\r-123,``)").readValueExpression().let {
            println(it.value)
            assert(it.value == "abc.xyz.m(null,false,a,-123,\"\")")
        }
    }

    @Test
    fun regexp() {
        SelectorParser("[x~=`.*`]")
            .readUnitSelectorExpression()
            .stringify().let {
                println(it)
                assert(it == "[x~=\".*\"]")
            }
        SelectorParser("[x!~=`\\\\d+`]")
            .readUnitSelectorExpression()
            .stringify().let {
                println(it)
                assert(it == "[x!~=\"\\\\d+\"]")
            }
    }

    @Test
    fun expression() {
        SelectorParser("((((a>null&&b>true&&c>1||d>1 || abc.xyz .m(null, false,a,\n\t\n-123,``)>``))))")
            .readExpression()
            .stringify().let {
                println(it)
                assert(it == "(a>null && b>true && c>1) || d>1 || abc.xyz.m(null,false,a,-123,\"\")>\"\"")
            }
        SelectorParser("View[a>1&&b>1&&c>1||d>1&&x^=1]")
            .readUnitSelectorExpression()
            .stringify().let {
                println(it)
                assert(it == "View[(a>1 && b>1 && c>1) || (d>1 && x^=1)]")
            }
    }

    @Test
    fun propertySegment() {
        SelectorParser("*").readPropertySegment()
        SelectorParser("View[a>1&&b>1&&c>1||d>1&&x^=1]").readPropertySegment()
        SelectorParser("@View[a>1&&b>1&&c>1||d>1&&x^=1][a!=b&&c>d.z11]").readPropertySegment()
        println(
            SelectorParser("[a='\\\"'][a=\"'\"][a=`\\x20\\n\\uD83D\\uDE04`][a=`\\x20`][a=\"`\u0020\"][a=`\\t\\n\\r\\b\\x00\\x09\\x1d`]").readPropertySegment()
                .stringify()
        )
    }

    @Test
    fun connectSegment() {
        assert(SelectorParser("+").readConnectSegment() == ConnectSegment(ConnectOperator.BeforeBrother))
        assert(SelectorParser("->").readConnectSegment() == ConnectSegment(ConnectOperator.Previous))
        assert(
            SelectorParser(">(1,2,3)").readConnectSegment() == ConnectSegment(
                ConnectOperator.Ancestor,
                TupleExpression(listOf(1, 2, 3))
            )
        )
        assert(
            SelectorParser("<<10n").readConnectSegment() == ConnectSegment(
                ConnectOperator.Descendant,
                PolynomialExpression(a = 10, b = 0)
            )
        )
        assert(
            SelectorParser("-(9n+1)").readConnectSegment() == ConnectSegment(
                ConnectOperator.AfterBrother,
                PolynomialExpression(a = 9, b = 1)
            )
        )
        assert(
            SelectorParser("-(-2n+9)").readConnectSegment() == ConnectSegment(
                ConnectOperator.AfterBrother,
                PolynomialExpression(a = -2, b = 9)
            )
        )
    }

    @Test
    fun selector() {
        SelectorParser("[_id=15] >(1,2,9) X + Z >(7+9n) *").readUnitSelectorExpression()
        SelectorParser("A > @B[a>''&&b>1&&c>1||d>1&&x^=1][a!=b&&c>d.z11][a>b.plus(null)] + C[a>b]").readUnitSelectorExpression()
        SelectorParser("A").readSelector()
        assert(
            SelectorParser("([text=`a`||id=`b`||vid=`c`])||([text=`d`||id=`e`||vid=`f`])")
                .readSelector()
                .fastQueryList == listOf(
                FastQuery.Text("a"),
                FastQuery.Id("b"),
                FastQuery.Vid("c"),
                FastQuery.Text("d"),
                FastQuery.Id("e"),
                FastQuery.Vid("f"),
            )
        )
        println(SelectorParser("(A + @B[a>1]) || (@C + D[f=``])").readSelector().toString())
    }


    @Test
    fun query_selector() {
        val text =
            "@[vid=\"rv_home_tab\"] <<(99-n) [vid=\"header_container\"] -(-2n+9) [vid=\"layout_refresh\"] +2 [vid=\"home_v10_frag_content\"]"
        val selector = Selector.parse(text)
        println("selector: $selector")
        val node = getSnapshotNode("https://i.gkd.li/i/14325747")
        val targets = transform.querySelectorAll(node, selector).toList()
        println("target_size: " + targets.size)
        println("target_id: " + targets.map { t -> t.id })
        assertTrue(targets.size == 1)
        println("id: " + targets.first().id)

        val trackTargets = transform.querySelectorAllContext(node, selector).toList()
        println("trackTargets_size: " + trackTargets.size)
        assertTrue(trackTargets.size == 1)
        println(trackTargets.first())
    }

    @Test
    fun check_tuple() {
        // 1->3, 3->21
        // 1,3->24
        val snapshotNode = getSnapshotNode("https://i.gkd.li/i/13247733")
        val (x1, x2) = (1..6).toList().shuffled().subList(0, 2).sorted()
        val x1N =
            transform.querySelectorAll(snapshotNode, Selector.parse("[_id=15] >$x1 *")).count()
        val x2N =
            transform.querySelectorAll(snapshotNode, Selector.parse("[_id=15] >$x2 *")).count()
        val x12N = transform.querySelectorAll(snapshotNode, Selector.parse("[_id=15] >($x1,$x2) *"))
            .count()

        println("$x1->$x1N, $x2->$x2N, ($x1,$x2)->$x12N")
    }

    @Test
    fun ast() {
        val ast = Selector.parseAst("(A + B) || (A +(3n+1) @B[a.b.c(a,b)>1 || b>null && (b~=`233` ) && a=1 ][b=true][(a>b || (c> 0  ))][(a>1 || a>1) && a>1])")
        println("selector: ${ast.value}")
        println("ast: ${ast.stringify()}")
    }
}
