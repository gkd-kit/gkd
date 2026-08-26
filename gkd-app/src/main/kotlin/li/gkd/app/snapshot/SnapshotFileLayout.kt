package li.gkd.app.snapshot

import java.io.File

class SnapshotFileLayout(
    private val root: File,
) {
    class Files(
        private val id: Long,
        val directory: File,
    ) {
        val snapshotFile: File
            get() = directory.resolve("$id.json")
        val minSnapshotFile: File
            get() = directory.resolve("$id.min.json")
        val webpFile: File
            get() = directory.resolve("$id.webp")
        val legacyPngFile: File
            get() = directory.resolve("$id.png")
        val screenshotFile: File
            get() = validScreenshotFile ?: webpFile.takeIf { it.exists() } ?: legacyPngFile
        val hasCompleteFiles: Boolean
            get() = snapshotFile.isFile && snapshotFile.length() > 0 &&
                validScreenshotFile != null

        private val validScreenshotFile: File?
            get() = when {
                webpFile.hasSupportedImageHeader() -> webpFile
                legacyPngFile.hasSupportedImageHeader() -> legacyPngFile
                else -> null
            }
    }

    fun committed(id: Long): Files {
        return Files(id, root.resolve(id.toString()))
    }

    fun staging(id: Long): Files {
        return Files(id, root.resolve(".$id.tmp"))
    }
}

private fun File.hasSupportedImageHeader(): Boolean {
    if (!isFile || length() < 2) return false
    return runCatching {
        val header = ByteArray(12)
        val size = inputStream().use { it.read(header) }
        fun matches(vararg bytes: Int): Boolean {
            return size >= bytes.size && bytes.indices.all { index ->
                header[index].toInt() and 0xff == bytes[index]
            }
        }
        matches(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ||
            matches(0xff, 0xd8, 0xff) ||
            matches(0x52, 0x49, 0x46, 0x46) &&
            size >= 12 && header.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray()) ||
            matches(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) ||
            matches(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) ||
            matches(0x42, 0x4d)
    }.getOrDefault(false)
}
