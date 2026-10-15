package li.gkd.app.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import li.gkd.app.platform.PlatformResult
import li.gkd.app.util.LogUtils
import li.songe.codeorigin.CallSite
import kotlin.time.TimeSource

object AppInfoRepository {
    fun initialize() {
        applicationScope().launch {
            // Source changes must not restart the package-ID debounce window.
            pending.map { it.keys }.distinctUntilChanged()
                .debounce(3_000).filter { it.isNotEmpty() }.collect { ids ->
                    background { update(source = "packagesChanged", appIds = ids) }
                }
        }
    }

    val state: StateFlow<AppCatalogState>
        field = MutableStateFlow(AppCatalogState())
    val snapshot get() = state.value.snapshot
    val snapshots = state.transform { current ->
        val snapshot = current.snapshot
        if (snapshot != null) emit(snapshot)
    }.distinctUntilChanged()
    val appInfoMapFlow = snapshots.map { it.apps }
    val visibleAppInfosFlow = snapshots.map { it.visibleApps }

    private val mutex = Mutex()
    private val pending = MutableStateFlow(emptyMap<String, Set<String>>())


    fun packagesChanged(ids: Set<String>, @CallSite source: String = "") {
        pending.update { queued ->
            queued + ids.associateWith { queued[it].orEmpty() + source }
        }
    }

    private suspend fun background(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // update records the failure and retains it in state.
        }
    }

    fun requestRefresh(@CallSite source: String = "") {
        applicationScope().launch { background { update(source = source) } }
    }

    fun privilegeChanged(@CallSite source: String = "") {
        applicationScope().launch { background { update(source = source, otherUsersOnly = true) } }
    }

    suspend fun refresh(@CallSite source: String = "") {
        update(source = source)
    }

    private suspend fun update(
        source: String,
        appIds: Set<String>? = null,
        otherUsersOnly: Boolean = false,
    ): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val startedAt = TimeSource.Monotonic.markNow()
            val packageSources = appIds?.let { ids ->
                pending.getAndUpdate { it - ids }.filterKeys { it in ids }
            }.orEmpty()
            val old = snapshot
            val queryAll = (appIds == null && !otherUsersOnly) || old == null
            val logContext = "source=$source appIds=$appIds " +
                    "otherUsersOnly=$otherUsersOnly queryAll=$queryAll packageSources=$packageSources"
            state.update { it.copy(refreshing = true, failure = null) }
            try {
                val query = openAppCatalog()
                val primary = if (queryAll) {
                    query.primaryApps().associateByTo(linkedMapOf()) { it.id }
                } else {
                    old.inventory.apps.associateByTo(linkedMapOf()) { it.id }.apply {
                        appIds.orEmpty().forEach { id ->
                            val app = query.primaryApp(id)
                            if (app == null) remove(id) else put(id, app)
                        }
                    }
                }
                val incomplete =
                    query.detectIncompleteList && primary.values.count { !it.isSystem } <= 4
                val abnormal =
                    if (queryAll) query.queryPackagesAbnormal || (query.canQueryPackages && incomplete)
                    else old.queryPackagesAbnormal
                if (queryAll && (!query.canQueryPackages || incomplete)) {
                    val extra = query.privilegedApps(query.userId)
                    val privileged = (extra as? PlatformResult.Success)?.value.orEmpty()
                    if (privileged.isNotEmpty()) privileged.forEach { primary[it.id] = it }
                    else query.visibleApps()
                        .forEach { primary.putIfAbsent(it.id, it.copy(hidden = false)) }
                }
                val users = query.otherUsers().filter { it.id != query.userId }
                val others = mutableListOf<AppInfo>()
                users.forEach { user ->
                    when (val result = query.privilegedApps(user.id)) {
                        is PlatformResult.Success -> others.addAll(result.value)
                        PlatformResult.Unsupported -> Unit
                    }
                }
                currentCoroutineContext().ensureActive()
                val next = AppCatalogSnapshot(
                    AppCatalog.normalize(
                        AppInventory(
                            query.userId,
                            primary.values.toList(),
                            users,
                            others
                        )
                    ),
                    query.launcherAppId, query.canQueryPackages, abnormal,
                )
                if (query.isCurrent()) {
                    query.committed(next)
                    state.value = AppCatalogState(snapshot = next)
                    LogUtils.d(
                        "应用列表更新完成 $logContext durationMs=${startedAt.elapsedNow().inWholeMilliseconds} " +
                                "primary=${next.inventory.apps.size} otherUsers=${next.inventory.otherUsers.size} " +
                                "others=${next.inventory.othersApps.size} visible=${next.visibleApps.size} " +
                                "queryPackagesAbnormal=$abnormal canQueryPackages=${query.canQueryPackages}",
                    )
                } else {
                    LogUtils.d(
                        "应用列表更新未提交：查询期间平台状态变化 $logContext " +
                                "durationMs=${startedAt.elapsedNow().inWholeMilliseconds}",
                    )
                }
            } catch (e: CancellationException) {
                LogUtils.d("应用列表更新已取消 $logContext durationMs=${startedAt.elapsedNow().inWholeMilliseconds}")
                throw e
            } catch (e: Exception) {
                state.update { it.copy(failure = e) }
                LogUtils.d("应用列表更新失败 $logContext durationMs=${startedAt.elapsedNow().inWholeMilliseconds}", e)
                throw e
            } finally {
                state.update { it.copy(refreshing = false) }
            }
        }
    }
}
