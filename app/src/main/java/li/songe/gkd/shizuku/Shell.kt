package li.songe.gkd.shizuku

import android.annotation.SuppressLint
import java.io.InputStream
import java.util.*


interface Shell {
    val isAvailable: Boolean

    fun exec(command: Command): Result
    fun exec(command: Command, inputPipe: InputStream): Result
    fun makeLiteral(arg: String): String
    class Command(command: String, vararg args: String) {
        private val mArgs = mutableListOf<String>()
        fun toStringArray(): Array<String?> {
            val array = arrayOfNulls<String>(mArgs.size)
            for (i in mArgs.indices) array[i] = mArgs[i]
            return array
        }

        override fun toString(): String {
            val sb = StringBuilder()
            for (i in mArgs.indices) {
                val arg = mArgs[i]
                sb.append(arg)
                if (i < mArgs.size - 1) sb.append(" ")
            }
            return sb.toString()
        }

        class Builder(command: String, vararg args: String) {
            private val mCommand: Command = Command(command, *args)
            fun addArg(argument: String): Builder {
                mCommand.mArgs.add(argument)
                return this
            }

            fun build(): Command {
                return mCommand
            }

        }

        init {
            mArgs.add(command)
            mArgs.addAll(args)
        }
    }

    open class Result(
        var cmd: Command,
        var exitCode: Int,
        var out: String,
        var err: String
    ) {
        val isSuccessful: Boolean
            get() = exitCode == 0

        @SuppressLint("DefaultLocale")
        override fun toString(): String {
            return String.format(
                "Command: %s\nExit code: %d\nOut:\n%s\n=============\nErr:\n%s",
                cmd,
                exitCode,
                out,
                err
            )
        }
    }
}