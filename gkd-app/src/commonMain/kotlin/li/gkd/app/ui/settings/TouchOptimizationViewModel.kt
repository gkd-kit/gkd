package li.gkd.app.ui.settings

import li.gkd.app.a11y.GestureService
import li.gkd.app.a11y.closeGestureServices
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

class TouchOptimizationViewModel : BaseViewModel() {
    suspend fun confirmOpenApp(service: GestureService): Boolean =
        MainViewModel.requireCurrent()
            .dialogRequests
            .confirm(
                title = service.name,
                text =
                    service.description.ifBlank {
                        getString(Res.string.touch_optimization_no_description)
                    },
                confirmText = getString(Res.string.touch_optimization_open_app),
                dismissOnRequest = true,
            )

    fun requestPrivilegeInfo() {
        scope.launchUi {
            val mainVm = MainViewModel.requireCurrent()
            if (
                mainVm.dialogRequests.confirm(
                    title = getString(Res.string.touch_optimization_privilege_title),
                    text = getString(Res.string.touch_optimization_privilege_description),
                    confirmText = getString(Res.string.action_go),
                    dismissOnRequest = true,
                )
            ) {
                mainVm.navigator.navigate(PrivilegeServiceRoute)
            }
        }
    }

    fun requestClose(services: List<GestureService>, keepGkd: Boolean = false) {
        scope.launchUi {
            val single = services.singleOrNull()
            val count = services.size.toString()
            val title =
                if (single != null) {
                    getString(Res.string.touch_optimization_confirm_one, single.name)
                } else {
                    getString(Res.string.touch_optimization_confirm_multiple, count)
                }
            val text =
                when {
                    single == null ->
                        services.joinToString("\n") {
                            if (it.name == it.appName) "• ${it.appName}"
                            else "• ${it.appName} · ${it.name}"
                        }
                    single.name == single.appName ->
                        getString(Res.string.touch_optimization_service_impact)
                    else ->
                        getString(Res.string.touch_optimization_service_provider, single.appName)
                }
            val confirmText =
                if (single != null) getString(Res.string.action_close)
                else getString(Res.string.touch_optimization_close_count, count)
            if (
                MainViewModel.requireCurrent()
                    .dialogRequests
                    .confirm(
                        title = title,
                        text =
                            if (keepGkd)
                                text + "\n\n" + getString(Res.string.touch_optimization_keep_gkd)
                            else text,
                        confirmText = confirmText,
                        dismissOnRequest = true,
                    )
            ) {
                if (
                    closeGestureServices(services.map { it.id }.toSet()) ==
                        PlatformResult.Unsupported
                ) {
                    ToastUtils.show(getString(Res.string.platform_action_unsupported))
                }
            }
        }
    }
}
