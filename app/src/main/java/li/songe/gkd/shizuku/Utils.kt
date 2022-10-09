package li.songe.gkd.shizuku

import java.io.PrintWriter
import java.io.StringWriter


object Utils {
    fun throwableToString(throwable: Throwable): String {
        val sw = StringWriter(1024)
        val pw = PrintWriter(sw)

        throwable.printStackTrace(pw)
        pw.close()

        return sw.toString()
    }
}