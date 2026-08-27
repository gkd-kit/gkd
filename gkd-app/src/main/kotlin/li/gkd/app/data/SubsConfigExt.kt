package li.gkd.app.data

import li.gkd.db.Db
import li.gkd.db.SubsConfig

suspend fun SubsConfig.SubsConfigDao.batchResetAppGroupEnable(
    subsItemId: Long,
    list: List<Pair<RawSubscription.RawAppGroup, RawSubscription.RawApp>>,
): List<Pair<RawSubscription.RawAppGroup, RawSubscription.RawApp>> = Db.withTransaction {
    list.filter { (group, app) ->
        resetAppGroupTypeEnable(subsItemId, app.id, group.key) > 0
    }
}
