package li.songe.ad_closer

import li.songe.ad_closer.util.MatchRule
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 4L)
        println(MatchRule.parse("ImageView[text=hi][id=hi] >> WebView[text=hi] - TextView"))
    }
}