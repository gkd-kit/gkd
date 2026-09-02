package li.gkd.selector

import li.gkd.selector.syntax.SelectorTokenizer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectorColdStartTest {
    @Test
    fun implicitAncestorCompilesInFreshJvmProcesses() {
        listOf("compile", "parse").forEach(::runProbe)
    }

    private fun runProbe(api: String) {
        val javaExecutable = File(
            System.getProperty("java.home"),
            if (System.getProperty("os.name").startsWith("Windows")) "bin/java.exe" else "bin/java",
        )
        val classpath = listOf(
            SelectorColdStartTest::class.java,
            Selector::class.java,
            SelectorTokenizer::class.java,
            Unit::class.java,
        ).map { type ->
            File(type.protectionDomain.codeSource.location.toURI()).absolutePath
        }.distinct().joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-cp",
            classpath,
            "li.gkd.selector.SelectorColdStartTestKt",
            api,
        ).redirectErrorStream(true).start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Cold-start probe timed out: $api")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), output)
        assertEquals("A B", output.trim())
    }
}

fun main(args: Array<String>) {
    val result = when (args.single()) {
        "compile" -> Selector.compile("A B").value
        "parse" -> Selector.parse("A B").value
        else -> error("Unknown cold-start probe")
    }
    print(result)
}
