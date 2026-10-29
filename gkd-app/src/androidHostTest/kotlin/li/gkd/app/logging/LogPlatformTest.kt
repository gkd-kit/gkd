package li.gkd.app.logging

import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LogPlatformTest {
    @Test
    fun platformErrorsAndWarningsKeepTheirPriority() {
        org.robolectric.shadows.ShadowLog.clear()
        writePlatformLog(LogLevel.ERROR, "logging-test", "error")
        writePlatformLog(LogLevel.WARN, "logging-test", "warning")
        assertEquals(
            listOf(android.util.Log.ERROR, android.util.Log.WARN),
            org.robolectric.shadows.ShadowLog.getLogsForTag("logging-test").map { it.type },
        )
    }

    @Test
    fun persistentWriteFailureReportsOnceAndCanRecover() {
        val project =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { it.resolve("settings.gradle.kts").isFile }
        val parent = project.resolve(".local/tests/android").apply { mkdirs() }
        val root = Files.createTempDirectory(parent.toPath(), "log-failure-").toFile()
        val directory = root.resolve("log").apply { writeText("blocked") }
        org.robolectric.shadows.ShadowLog.clear()
        try {
            FileLogWriter(directory, LogMetadata("Android", "9", "test", "test")).use { writer ->
                repeat(3) {
                    writer.append(
                        "test",
                        "caller",
                        "loc",
                        listOf("failure-$it"),
                        System.currentTimeMillis(),
                    )
                    org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                        writer.flush()
                    }
                }
                assertEquals(
                    1,
                    org.robolectric.shadows.ShadowLog.getLogsForTag("GKD").count {
                        it.msg.startsWith("Log file write failed:")
                    },
                )
                assertEquals(3L, writer.status().failedBatches)
                assertTrue(directory.delete())
                writer.append(
                    "test",
                    "caller",
                    "loc",
                    listOf("recovered"),
                    System.currentTimeMillis(),
                )
                writer.flush()
                assertTrue(directory.listFiles()!!.single().readText().contains("recovered"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun androidValuesAreRenderedBeforeBackgroundWriting() {
        val project =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { it.resolve("settings.gradle.kts").isFile }
        val parent = project.resolve(".local/tests/android").apply { mkdirs() }
        val root = Files.createTempDirectory(parent.toPath(), "logging-").toFile()
        try {
            FileLogWriter(root, LogMetadata("Android", "9 (28)", "test", "test (1)")).use { writer
                ->
                val bundle = Bundle().apply { putString("payload", "before") }
                val intent =
                    Intent(
                            "test.action",
                            android.net.Uri.parse(
                                "https://user:password@example.com/path?token=secret#fragment",
                            ),
                        )
                        .putExtra("nested", bundle)
                val values =
                    listOf(intent, IllegalArgumentException("failure detail")).map(::formatLogValue)
                writer.append("test", "caller", "test-location", values, System.currentTimeMillis())
                bundle.putString("payload", "after")
                writer.flush()
                val text = root.listFiles()!!.single().readText()
                assertTrue(text, text.contains("action=test.action"))
                assertTrue(text, text.contains("data=https://example.com/path"))
                assertTrue(text, text.contains("keys=[nested]"))
                assertFalse(text, text.contains("secret"))
                assertFalse(text, text.contains("password"))
                assertFalse(text, text.contains("payload="))
                assertTrue(text, text.contains("IllegalArgumentException: failure detail"))
                assertTrue(text, text.contains("Android: 9 (28)\nDevice: test\nApp: test (1)\n"))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
