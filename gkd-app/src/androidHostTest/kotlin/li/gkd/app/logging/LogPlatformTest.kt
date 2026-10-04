package li.gkd.app.logging

import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LogPlatformTest {
    @Test fun androidValuesAreRenderedBeforeBackgroundWriting() {
        val project = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { it.resolve("settings.gradle.kts").isFile }
        val parent = project.resolve(".local/tests/android").apply { mkdirs() }
        val root = Files.createTempDirectory(parent.toPath(), "logging-").toFile()
        try {
            FileLogWriter(root, LogMetadata("Android", "9 (28)", "test", "test (1)")).use { writer ->
                val bundle = Bundle().apply { putString("payload", "before") }
                val intent = Intent("test.action").putExtra("nested", bundle)
                val values = listOf(intent, IllegalArgumentException("failure detail"))
                    .map(::formatLogValue)
                writer.append("test", "caller", "test-location", values, System.currentTimeMillis())
                bundle.putString("payload", "after")
                writer.flush()
                val text = root.listFiles()!!.single().readText()
                assertTrue(text, text.contains("Intent{action=test.action,extras=Bundle{nested=Bundle{payload=before}}}"))
                assertTrue(text, text.contains("IllegalArgumentException: failure detail"))
                assertTrue(text, text.contains("Android: 9 (28)\nDevice: test\nApp: test (1)\n"))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
