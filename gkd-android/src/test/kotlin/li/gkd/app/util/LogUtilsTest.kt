package li.gkd.app.util

import android.content.Intent
import android.os.Bundle
import java.io.File
import java.nio.file.Files
import li.gkd.app.AndroidResourcesTest
import li.gkd.app.logging.LogMetadata
import li.gkd.app.util.LogUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUtilsTest : AndroidResourcesTest() {
    @Test fun sharedLoggerPreservesAndroidValues() {
        val project = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { it.resolve("settings.gradle.kts").isFile }
        val parent = project.resolve(".local/tests/android").apply { mkdirs() }
        val root = Files.createTempDirectory(parent.toPath(), "logging-").toFile()
        try {
            LogUtils.initialize(root, LogMetadata("Android", "9 (28)", "test", "test (1)"), false)
            try {
                val bundle = Bundle().apply { putString("payload", "before") }
                val intent = Intent("test.action").putExtra("nested", bundle)
                LogUtils.d(intent, IllegalArgumentException("failure detail"))
                bundle.putString("payload", "after")
                LogUtils.flush()
                val text = root.listFiles()!!.single().readText()
                assertTrue(text, text.contains("Intent{action=test.action,extras=Bundle{nested=Bundle{payload=before}}}"))
                assertTrue(text, text.contains("IllegalArgumentException: failure detail"))
                assertTrue(text, text.contains("Android: 9 (28)\nDevice: test\nApp: test (1)\n"))
            } finally {
                LogUtils.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
