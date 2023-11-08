package li.songe.gkd.util

import li.songe.gkd.app

private val filesDir by lazy {
    app.getExternalFilesDir(null) ?: app.filesDir
}
val dbFolder by lazy { filesDir.resolve("db") }
val subsFolder by lazy { filesDir.resolve("subscription") }
val snapshotFolder by lazy { filesDir.resolve("snapshot") }

private val cacheDir by lazy {
    app.externalCacheDir ?: app.cacheDir
}
val snapshotZipDir by lazy { cacheDir.resolve("snapshotZip") }
val newVersionApkDir by lazy { cacheDir.resolve("newVersionApk") }
val logZipDir by lazy { cacheDir.resolve("logZip") }

fun initFolder() {
    listOf(
        dbFolder, subsFolder, snapshotFolder, snapshotZipDir, newVersionApkDir, logZipDir
    ).forEach { f ->
        if (!f.exists()) {
            f.mkdirs()
        }
    }
}