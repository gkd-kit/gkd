package li.gkd.app.util

import android.app.Application
import android.os.Binder
import android.os.ParcelFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowParcelFileDescriptor
import org.robolectric.util.ReflectionHelpers
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    sdk = [28],
    shadows = [BinderDumpTest.BlockingPipe::class],
)
class BinderDumpTest {
    @Test
    fun repeatedDumpReadsCurrentOutputAndForwardsArguments() {
        val binder = DumpBinder("first state\n")
        assertEquals("first state\n", binder.dump())
        assertEquals(emptyList<String>(), binder.arguments)

        binder.output = "updated state\n"
        assertEquals("updated state\n", binder.dump("visible-apps"))
        assertEquals(listOf("visible-apps"), binder.arguments)
    }

    @Test
    fun permissionDenialTextThrowsInsteadOfReturningDumpData() {
        val binder = DumpBinder(
            "Permission Denial: can't dump AccessibilityManagerService due to missing android.permission.DUMP permission",
        )
        val error = assertThrows(SecurityException::class.java) { binder.dump() }
        assertEquals(binder.output, error.message)
    }

    @Test
    fun emptyDumpFailsInsteadOfReturningNoState() {
        val binder = DumpBinder("")
        val error = assertThrows(IllegalStateException::class.java) { binder.dump() }
        assertEquals("System service dump is empty", error.message)
    }

    // Robolectric implements pipes with ordinary files, whose readers otherwise see EOF before dump writes.
    @Implements(ParcelFileDescriptor::class)
    class BlockingPipe : ShadowParcelFileDescriptor() {
        @RealObject
        private lateinit var descriptor: ParcelFileDescriptor

        @Implementation
        override fun getFileDescriptor(): FileDescriptor {
            if (descriptor === readEnd) {
                check(written.await(5, TimeUnit.SECONDS)) { "Test dump did not finish writing" }
            }
            return super.getFileDescriptor()
        }

        companion object {
            private var readEnd: ParcelFileDescriptor? = null
            var written = CountDownLatch(1)

            @JvmStatic
            @Implementation(methodName = "createPipe")
            fun createBlockingPipe(): Array<ParcelFileDescriptor> {
                written = CountDownLatch(1)
                return ReflectionHelpers.callStaticMethod<Array<ParcelFileDescriptor>>(
                    ShadowParcelFileDescriptor::class.java, "createPipe",
                ).also { readEnd = it[0] }
            }
        }
    }

    private class DumpBinder(var output: String) : Binder() {
        var arguments: List<String>? = null

        override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
            arguments = args?.toList().orEmpty()
            writer.print(output)
            writer.flush()
            BlockingPipe.written.countDown()
        }
    }
}
