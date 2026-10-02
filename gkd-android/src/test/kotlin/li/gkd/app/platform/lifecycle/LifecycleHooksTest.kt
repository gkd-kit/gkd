package li.gkd.app.platform.lifecycle

import android.app.Application
import java.io.File
import java.nio.file.Files
import li.gkd.app.logging.LogMetadata
import li.gkd.app.util.LogUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LifecycleHooksTest {
    @Test
    fun lifecycleLoggingUsesOwnerAndRegistrationLocation() {
        val events = mutableListOf<String>()
        val hooks = LifecycleHooks(
            onLifecycleEvent = { event, loc -> events += "$event@$loc" },
            onCleanupError = { error, _ -> throw error },
        )
        hooks.useLogLifecycle(this, loc = "lifecycle-registration-loc")
        hooks.onCreated { events += "created-callback" }
        hooks.onDestroyed { events += "destroyed-callback" }

        hooks.dispatchCreated()
        hooks.dispatchDestroyed()

        assertEquals(
            listOf(
                "onCreated -> LifecycleHooksTest@lifecycle-registration-loc",
                "created-callback",
                "destroyed-callback",
                "onDestroyed -> LifecycleHooksTest@lifecycle-registration-loc",
            ),
            events,
        )
    }

    @Test
    fun callbacksRunOnceInLifecycleOrder() {
        val events = mutableListOf<String>()
        val hooks = LifecycleHooks { error, _ -> throw error }
        hooks.onCreated { events += "create-first" }
        hooks.onCreated { events += "create-second" }
        hooks.onDestroyed { events += "destroy-first" }
        hooks.onDestroyed { events += "destroy-second" }

        hooks.dispatchCreated()
        hooks.dispatchCreated()
        hooks.dispatchDestroyed()
        hooks.dispatchDestroyed()

        assertEquals(
            listOf("create-first", "create-second", "destroy-second", "destroy-first"),
            events,
        )
    }

    @Test
    fun cleanupFailureDoesNotSkipRemainingCallbacks() {
        val events = mutableListOf<String>()
        val errors = mutableListOf<Pair<Throwable, String>>()
        val hooks = LifecycleHooks { error, loc -> errors += error to loc }
        hooks.onDestroyed { events += "first" }
        hooks.onDestroyed(loc = "failed-cleanup-loc") { error("failed cleanup") }
        hooks.onDestroyed { events += "last" }

        hooks.dispatchDestroyed()

        assertEquals(listOf("last", "first"), events)
        assertEquals(listOf("failed cleanup"), errors.map { it.first.message })
        assertEquals(listOf("failed-cleanup-loc"), errors.map { it.second })
    }

    @Test
    fun resourceSlotClosesReplacedAndCurrentResources() {
        val events = mutableListOf<String>()
        val first = AutoCloseable { events += "first" }
        val second = AutoCloseable { events += "second" }
        val slot = ResourceSlot<AutoCloseable>()

        assertSame(first, slot.replace(first))
        assertSame(second, slot.replace(second))
        slot.close()
        slot.close()

        assertEquals(listOf("first", "second"), events)
    }

    @Test
    fun resourceSlotClosesOldResourceBeforeCreatingReplacement() {
        val events = mutableListOf<String>()
        val slot = ResourceSlot<AutoCloseable>()
        slot.replace(AutoCloseable { events += "closed" })

        val error = assertThrows(IllegalStateException::class.java) {
            slot.replace {
                events += "create"
                error("failed replacement")
            }
        }
        slot.close()

        assertEquals("failed replacement", error.message)
        assertEquals(listOf("closed", "create"), events)
    }

    @Test
    fun resourceSlotReportsResourceRegistrationLocation() {
        val project = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { it.resolve("settings.gradle.kts").isFile }
        val parent = project.resolve(".local/tests/android").apply { mkdirs() }
        val root = Files.createTempDirectory(parent.toPath(), "resource-slot-").toFile()
        try {
            LogUtils.initialize(root, LogMetadata("Android", "9 (28)", "test", "test (1)"), false)
            try {
                ResourceSlot<AutoCloseable>().use { slot ->
                    slot.replace(loc = "resource-registration-loc") {
                        AutoCloseable { error("failed close") }
                    }
                }
                LogUtils.flush()
                val text = root.listFiles()!!.single().readText()
                assertTrue(text, text.contains("IllegalStateException: failed close"))
                assertTrue(text, text.contains("resource-registration-loc"))
            } finally {
                LogUtils.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
