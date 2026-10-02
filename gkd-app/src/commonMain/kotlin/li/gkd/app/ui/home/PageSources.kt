package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.state.Loadable
import li.gkd.db.Db

val scopeAppSource by lazy {
    combine(
        AppInfoRepository.visibleAppInfosFlow,
        Db.actionLogDao.queryLatestUniqueAppIds(),
        Db.appLastVisitDao.query()
    ) { apps, order, visits ->
        AppListSources.build(apps, order, visits)
    }
}

@Composable
fun rememberVisitOrder(): Loadable<Map<String, Int>> = remember {
    Db.appLastVisitDao.query().map { ids ->
        Loadable.Ready(ids.mapIndexed { index, id -> id to index }.toMap())
    }
}.collectAsStateWithLifecycle<Loadable<Map<String, Int>>>(Loadable.Loading).value

@Composable
fun appLabel(id: String): String =
    AppInfoRepository.state.collectAsStateWithLifecycle().value.snapshot?.apps?.get(id)?.name ?: id
