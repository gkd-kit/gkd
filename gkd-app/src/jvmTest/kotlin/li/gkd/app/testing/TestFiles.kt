package li.gkd.app.testing

import java.io.File
import java.util.UUID

object TestFiles {
    val root: File by lazy {
        val project =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { it.resolve("settings.gradle.kts").isFile }
        project.resolve(".local/tests/desktop/shared-jvm-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "Cannot create isolated test directory: $this" }
        }
    }
}
