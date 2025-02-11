package li.songe.selector

import kotlin.test.Test

class QueryUnitTest {

    val node1 by lazy { getSnapshotNode("https://i.gkd.li/i/13247733") }
    val node2 by lazy { getSnapshotNode("https://i.gkd.li/i/14384152") }
    val node3 by lazy { getSnapshotNode("https://i.gkd.li/i/13247610") }

    @Test
    fun regexp() {
        val selector = Selector.parse("[text~=`.*\\\\d+`]")
        println(selector)
        val nodes = transform.querySelectorAll(node1, selector).toList()
        assert(nodes.size == 27)
        assert(nodes.subList(0, 5).map { it.id } == listOf(126, 124, 105, 106, 102))
    }

    @Test
    fun example1() {
        val selector = Selector.parse(
            "@TextView[getPrev(0).text=`签到提醒`] - [text=`签到提醒`] <<n [vid=`webViewContainer`]"
        )
        println(selector)
        val nodes = transform.querySelectorAll(node2, selector).toList()
        assert(nodes.single().id == 32)
    }

    @Test
    fun example2() {
        val selector = Selector.parse(
            "@[id=\"com.byted.pangle.m:id/tt_splash_skip_btn\"] <<n [id=\"com.coolapk.market:id/ad_container\"]"
        )
        println(selector)
        val nodes = transform.querySelectorAll(node3, selector).toList()
        assert(nodes.single().id == 17)
    }
}