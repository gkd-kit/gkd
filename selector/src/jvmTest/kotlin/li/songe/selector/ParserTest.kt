package li.songe.selector

import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import li.songe.selector.parser.ParserSet
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream


class ParserTest {
    private val projectCwd = File("../").absolutePath
    private val assetsDir = File("$projectCwd/_assets").apply {
        if (!exists()) {
            mkdir()
        }
    }
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val transform = Transform<TestNode>(getAttr = { node, name ->
        if (name == "_id") return@Transform node.id
        if (name == "_pid") return@Transform node.pid
        val value = node.attr[name] ?: return@Transform null
        if (value is JsonNull) return@Transform null
        value.intOrNull ?: value.booleanOrNull ?: value.content
    }, getName = { node -> node.attr["name"]?.content }, getChildren = { node ->
        node.children.asSequence()
    }, getParent = { node -> node.parent })

    private val idToSnapshot = HashMap<String, TestNode>()

    private fun getOrDownloadNode(url: String): TestNode {
        val githubAssetId = url.split('/').last()
        idToSnapshot[githubAssetId]?.let { return it }

        val file = assetsDir.resolve("$githubAssetId.json")
        if (!file.exists()) {
            URL("https://github.com/gkd-kit/inspect/files/${githubAssetId}/file.zip").openStream()
                .use { inputStream ->
                    val zipInputStream = ZipInputStream(inputStream)
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".json")) {
                            val outputStream = BufferedOutputStream(FileOutputStream(file))
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            outputStream.close()
                            break
                        }
                        entry = zipInputStream.nextEntry
                    }
                    zipInputStream.closeEntry()
                    zipInputStream.close()
                }
        }
        val nodes = json.decodeFromString<TestSnapshot>(file.readText()).nodes

        nodes.forEach { node ->
            node.parent = nodes.getOrNull(node.pid)
            node.parent?.apply {
                children.add(node)
            }
        }
        return nodes.first().apply {
            idToSnapshot[githubAssetId] = this
        }
    }

    @Test
    fun test_expression() {
        println(ParserSet.expressionParser("a>1&&b>1&&c>1||d>1", 0).data)
        println(Selector.parse("View[a>1&&b>1&&c>1||d>1&&x^=1] > Button[a>1||b*='zz'||c^=1]"))
        println(Selector.parse("[id=`com.byted.pangle:id/tt_splash_skip_btn`||(id=`com.hupu.games:id/tv_time`&&text*=`跳过`)]"))
    }

    @Test
    fun string_selector() {
        val text =
            "ImageView < @FrameLayout < LinearLayout < RelativeLayout <n LinearLayout < RelativeLayout + LinearLayout > RelativeLayout > TextView[text\$='广告']"
        val selector = Selector.parse(text)
        println("trackIndex: " + selector.trackIndex)
        println("canCacheIndex: " + Selector.parse("A + B").canCacheIndex)
        println("canCacheIndex: " + Selector.parse("A > B - C").canCacheIndex)
    }

    @Test
    fun query_selector() {
        val text =
            "@[vid=\"rv_home_tab\"] <<(99-n) [vid=\"header_container\"] -(-2n+9) [vid=\"layout_refresh\"] +2 [vid=\"home_v10_frag_content\"]"
        val selector = Selector.parse(text)
        println("selector: $selector")
        val node = getOrDownloadNode("https://i.gkd.li/i/14325747")
        val targets = transform.querySelectorAll(node, selector).toList()
        println("target_size: " + targets.size)
        println("target_id: " + targets.map { t -> t.id })
        assertTrue(targets.size == 1)
        println("id: " + targets.first().id)

        val trackTargets = transform.querySelectorTrackAll(node, selector).toList()
        println("trackTargets_size: " + trackTargets.size)
        assertTrue(trackTargets.size == 1)
        println(trackTargets.first().mapIndexed { index, testNode ->
            testNode.id to selector.tracks[index]
        })
    }

    @Test
    fun check_parser() {
        val selector = Selector.parse("View > Text[index>-0]")
        println("selector: $selector")
        println("canCacheIndex: " + selector.canCacheIndex)
    }


    @Test
    fun check_query() {
        val text = "@TextView - [text=\"签到提醒\"] <<n [vid=\"webViewContainer\"]"
        val selector = Selector.parse(text)
        println("selector: $selector")
        println(selector.trackIndex)
        println(selector.tracks.toList())

        val node = getOrDownloadNode("https://i.gkd.li/i/14384152")
        val targets = transform.querySelectorAll(node, selector).toList()
        println("target_size: " + targets.size)
        println(targets.firstOrNull())
    }

    @Test
    fun check_quote() {
//        https://github.com/gkd-kit/inspect/issues/7
        val selector = Selector.parse("a[a='\\\\'] ")
        println("check_quote:$selector")
    }

    @Test
    fun check_escape() {
        val source =
            "[a='\\\"'][a=\"'\"][a=`\\x20\\n\\uD83D\\uDE04`][a=`\\x20`][a=\"`\u0020\"][a=`\\t\\n\\r\\b\\x00\\x09\\x1d`]"
        println("source:$source")
        val selector = Selector.parse(source)
        println("check_quote:$selector")
    }

    @Test
    fun check_tuple() {
        val source = "[_id=15] >(1,2,9) X + Z >(7+9n) *"
        println("source:$source")
        val selector = Selector.parse(source)
        println("check_quote:$selector")

        // 1->3, 3->21
        // 1,3->24
        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/13247733")
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
    fun check_descendant() {
        // ad_container 符合 quickFind, 目标节点 tt_splash_skip_btn 在其内部但不符合 quickFind
        val source =
            "@[id=\"com.byted.pangle.m:id/tt_splash_skip_btn\"] <<n [id=\"com.coolapk.market:id/ad_container\"]"
        println("source:$source")
        val selector = Selector.parse(source)
        println("selector:$selector")
        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/13247610")
        println("result:" + transform.querySelectorAll(snapshotNode, selector).map { n -> n.id }
            .toList())
    }

    @Test
    fun check_regex() {
        val source = "[vid~=`(?is)TV.*`]"
        println("source:$source")
        val selector = Selector.parse(source)
        val snapshotNode = getOrDownloadNode("https://i.gkd.li/i/14445410")
        println("selector:$selector")
        println("result:" + transform.querySelectorAll(snapshotNode, selector).map { n -> n.id }
            .toList())
    }
}