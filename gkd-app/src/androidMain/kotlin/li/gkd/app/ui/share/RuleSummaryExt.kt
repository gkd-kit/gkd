package li.gkd.app.ui.share

import li.gkd.app.resources.Res
import li.gkd.app.resources.config_loading
import li.gkd.app.resources.data_load_failed
import li.gkd.app.rule.RuleSummary
import li.gkd.app.state.Loadable
import li.gkd.app.ui.home.HomeDataText
import li.gkd.app.ui.text.getSync

fun RuleSummary.statusText(actionCount: Long): String = HomeDataText.summary(
    globalGroups.size, appSize, appGroupSize, actionCount
)

fun Loadable<RuleSummary>.statusText(actionCount: Long): String = when (this) {
    Loadable.Loading -> Res.string.config_loading.getSync()
    is Loadable.Failure -> Res.string.data_load_failed.getSync()
    is Loadable.Ready -> value.statusText(actionCount)
}
