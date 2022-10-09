package li.songe.node_selector

import li.songe.node_selector.parser.Tools.gkdSelectorParser
import org.json.JSONObject
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
    }

    @Test
    fun check_combinatorUnit() {
    }

    @Test
    fun check_json() {
//        View > View[text='']
        val js = JSONObject("{\"key\":\"\\u6211\\u0020尼\\\\x20玛逼\\u8349\\u6ce5\\u9a6c\"}")

        println(js.getString("key"))
    }

    @Test
    fun check_property() {
        val source = "View[k^=.3] <2 P + Z - G <2 P[k=1][k==2] T Sing V > V(1.%, 50%)"
        println("source:$source")
//        val result = combinatorPositionSelectorParser(source, 0)
//        println("first:"+result.data.first.first)
//        result.data.first.second.forEach {
//            println("item:$it")
//        }
//        println("click_position:"+result.data.second)
        val gkdRule = gkdSelectorParser(source)
        println("result:$gkdRule")
    }
}