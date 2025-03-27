package li.songe.selector

import kotlin.test.Test

class QueryUnitTest {

    val node1 by lazy { getSnapshotNode("https://i.gkd.li/i/13247733") }
    val node2 by lazy { getSnapshotNode("https://i.gkd.li/i/14384152") }
    val node3 by lazy { getSnapshotNode("https://i.gkd.li/i/13247610") }
    val node4 by lazy { getSnapshotNode("https://i.gkd.li/i/16076188") }

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
            "@[id=`com.byted.pangle.m:id/tt_splash_skip_btn`] <<n [id=`com.coolapk.market:id/ad_container`]"
        )
        println(selector)
        val nodes = transform.querySelectorAll(node3, selector).toList()
        assert(nodes.single().id == 17)
    }

    @Test
    fun example3() {
        val selector = Selector.parse(
            "[text=`搜索历史`] + [_id=161] -> [text=`清除`] <2 [id=`com.coolapk.market:id/close_view`]"
        )
        println(selector)
        val nodes = transform.querySelectorAll(node1, selector).toList()
        assert(nodes.single().id == 161)
        val nodes2 = transform.querySelectorAllContext(node1, selector).toList()
        val pathList = nodes2.single().unitResults.single().getNodeConnectPath(transform)
        val pathText = pathList.map {
            "${it.target.id} ${it.formatConnectOffset} ${it.source.id}"
        }.toString()
        println(pathText)
        assert(pathText == "[163 < 161, 161 -> 163, 160 + 161]")
    }

    @Test
    fun example4() {
        val selector = Selector.parse(
            "([_id=62] <<n [_id=28]) && (ImageView < FrameLayout <n ViewGroup[desc^=\"直播\"] - ViewGroup >4 FrameLayout[index=0] +2 FrameLayout > @TextView[index=parent.childCount.minus(1)] <<n FrameLayout[vid=\"content\"])"
        )
        println(selector)
        val nodes = transform.querySelectorAllContext(node4, selector).toList()
        val pathList = nodes.single().unitResults
        println(pathList.size)
        assert(pathList.size == 2)
        println(pathList.map { p ->
            p.unitResults.single().getNodeConnectPath(transform).map {
                "${it.target.id} ${it.formatConnectOffset} ${it.source.id}"
            }
        })
    }

    @Test
    fun example5() {
        val selector = Selector.parse("[parent=null]")
        println(selector)
        val targetNode = selector.match(node4, transform, MatchOption.default)
        assert(node4 === targetNode)
        val nodes = transform.querySelectorAllContext(node4, selector).toList()
        assert(nodes.isEmpty())
    }
}