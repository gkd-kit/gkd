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

object AppInfoRepository {
    fun initialize() {
        applicationScope().launch {
            pending.debounce(3_000).filter { it.isNotEmpty() }.collect { ids ->
                background { update(appIds = ids) }
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
    private val pending = MutableStateFlow(emptySet<String>())


    fun packagesChanged(ids: Set<String>) {
        pending.update { it + ids }
    }

    private suspend fun background(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.d("app catalog refresh failed", e)
        }
    }

    fun requestRefresh() {
        applicationScope().launch { background { refresh() } }
    }

    fun privilegeChanged() {
        applicationScope().launch { background { update(otherUsersOnly = true) } }
    }

    suspend fun refresh() = update()

    private suspend fun update(
        appIds: Set<String>? = null,
        otherUsersOnly: Boolean = false,
    ): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (appIds != null) pending.update { it - appIds }
            state.update { it.copy(refreshing = true, failure = null) }
            try {
                val query = openAppCatalog()
                val old = snapshot
                val queryAll = (appIds == null && !otherUsersOnly) || old == null
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
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(failure = e) }
                throw e
            } finally {
                state.update { it.copy(refreshing = false) }
            }
        }
    }
}
