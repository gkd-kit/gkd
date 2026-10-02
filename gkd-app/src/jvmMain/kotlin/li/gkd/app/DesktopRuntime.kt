package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.PlatformContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.applicationScope
import li.gkd.app.crash.CrashMetadata
import li.gkd.app.crash.CrashRecorder
import li.gkd.app.crash.FileCrashStorage
import li.gkd.app.network.NetworkClients
import li.gkd.app.record.RuntimeRecordRepository
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.storage.LogArchive
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.appStorage
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.image.ImageLoaders
import li.gkd.app.ui.text.subscriptionDefaults
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem
import li.gkd.db.initialize

/** Application-owned repositories outlive routes and explicit UI scenario reloads. */
class DesktopRuntime(
    val simulator: SimulatorStore
) : AutoCloseable {
    private val scope = applicationScope()
    val appCatalog = DesktopAppCatalog(simulator)

    companion object {
        @Volatile
        private var current: DesktopRuntime? = null

        fun requireCurrent(): DesktopRuntime =
            checkNotNull(current) { "Desktop runtime is not active" }
    }

    @Composable
    fun appIcon(id: String): BitmapPainter? {
        val catalog by AppInfoRepository.state.collectAsStateWithLifecycle()
        return remember(id, catalog.snapshot, catalog.refreshing) { DesktopProfile.readIcon(id) }
    }

    init {
        LogUtils.d("Desktop runtime started")
    }

    private val pendingProfile = DesktopStorage.sessionDirectory.resolve("profile-import-pending")
    private val importProfile = !appStorage().database.isFile || pendingProfile.isFile

    init {
        if (importProfile) pendingProfile.writeText("1")
    }

    init {
        Db.initialize(appStorage().database.absolutePath)
    }

    private val sharedImageLoader = lazy {
        ImageLoaders.create(PlatformContext.INSTANCE, appStorage().coilCache)
    }
    val imageLoader: ImageLoader by sharedImageLoader

    private val crashRecorder = CrashRecorder(
        CrashMetadata(
            device = "Desktop " + System.getProperty("os.name"),
            osCode = 0,
            osName = System.getProperty("os.version"),
            versionCode = DesktopProfile.versionCode.toIntOrNull() ?: 0,
            versionName = DesktopProfile.versionName
        )
    )
    private val previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val crashHandler = Thread.UncaughtExceptionHandler { thread, error ->
        LogUtils.d("UncaughtExceptionHandler", thread, error)
        runCatching { crashRecorder.record(thread, error) }.onFailure { it.printStackTrace() }
        runCatching { LogUtils.flush() }.onFailure { it.printStackTrace() }
        previousCrashHandler?.uncaughtException(thread, error) ?: error.printStackTrace()
    }

    init {
        synchronized(Companion) {
            check(current == null) { "Desktop runtime is already active" }
            current = this
        }
        AppInfoRepository.initialize()
        simulator.onAppCatalogChanged = { AppInfoRepository.requestRefresh() }
        AppInfoRepository.requestRefresh()
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)
    }

    init {
        scope.launch { StorageMaintenance.clearExpired(appStorage()); FileCrashStorage.trim() }
    }

    val subscriptionInitialization = scope.launch {
        SubscriptionRepository.initialize(subscriptionDefaults())
        val imported = DesktopStorage.sessionDirectory.resolve("profile-imported")
        if (!imported.exists()) {
            val existing = Db.subsItemDao.queryAll().mapTo(mutableSetOf()) { it.id }

            DesktopProfile.directory.resolve("subscriptions").takeIf { importProfile }?.listFiles()
                .orEmpty().filter { it.extension == "json" }.forEachIndexed { index, file ->
                    val value =
                        RawSubscription.parse(file.readText(), json5 = false)
                    if (value.id in existing && value.id != LOCAL_SUBS_ID) return@forEachIndexed
                    val enabled = DesktopProfile.subscriptionEnabled[value.id] ?: true
                    SubscriptionRepository.saveWithItem(
                        value,
                        SubsItem(
                            id = value.id,
                            order = index,
                            enable = enabled,
                            updateUrl = value.updateUrl
                        )
                    )
                }
            imported.writeText("1")
            check(!pendingProfile.exists() || pendingProfile.delete()) { "Cannot clear profile import marker" }
        }
        DesktopProfile.directory.resolve("fixtures.json").takeIf { it.isFile }?.let {
            importDesktopFixtures(
                it,
                appStorage().snapshot,
                DesktopStorage.sessionDirectory.resolve("fixtures-imported")
            )
        }
    }

    suspend fun exportLogs(): File = withContext(Dispatchers.IO) {
        LogUtils.flush()
        LogArchive.build(
            mapOf("desktop.txt" to { "GKD Desktop development host\n" + System.getProperty("os.name") })
        )
    }

    override fun close() {
        simulator.onAppCatalogChanged = null
        if (Thread.getDefaultUncaughtExceptionHandler() === crashHandler) Thread.setDefaultUncaughtExceptionHandler(
            previousCrashHandler
        )
        try {
            runBlocking {
                try {
                    RuntimeRecordRepository.flush()
                } finally {
                    SettingsRepository.awaitPersistence()
                }
            }
        } catch (e: Exception) {
            LogUtils.d("Desktop closed with unsaved data", e)
        } finally {
            try {
                runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
                if (sharedImageLoader.isInitialized()) imageLoader.shutdown()
                NetworkClients.close()
                Db.close()
            } finally {
                synchronized(Companion) {
                    if (current === this) current = null
                }
                LogUtils.d("Desktop runtime closed")
                LogUtils.close()
            }
        }
    }
}
