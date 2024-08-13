package li.songe.json5

import kotlin.test.Test

class Json5Test {

    @Test
    fun parse() {
        // https://github.com/json5/json5/blob/main/test/parse.js
        val element = Json5.parseToJson5Element("[1,2,3,'\\x002\\n']/*23*///1")
        println("element: $element")
    }

    @Test
    fun format() {
        val element = Json5.parseToJson5Element("{'a-1':1,b:{c:['d',{f:233}]}}")
        println("element: $element")
        val formatted = Json5.encodeToString(element, 2)
        println("formatted:\n$formatted")
    }
}
