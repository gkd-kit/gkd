package li.gkd.app

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import li.gkd.app.resources.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import li.gkd.app.permission.AndroidPermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@Composable
fun DesktopControls(
    state: DesktopState,
    session: DesktopSession,
    persistence: SimulatorPersistence,
    flushSettings: () -> Unit,
    onBack: () -> Unit
) {
    val settings by state.simulator.settings.collectAsStateWithLifecycle()
    val saveStatus by persistence.status.collectAsStateWithLifecycle()
    val environment = settings.environment()
    val android = environment.android
    fun update(transform: (DesktopEnvironment) -> DesktopEnvironment) {
        state.simulatorCommand {
            state.simulator.updateEnvironment(transform)
        }
    }

    fun system(transform: (AndroidWindow) -> AndroidWindow) =
        update { it.copy(android = transform(it.android)) }
    Scaffold(topBar = { GkTopAppBar(title = { Text("模拟设置") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "控制项只影响开发环境；模拟状态不代表真机状态。",
                style = MaterialTheme.typography.bodySmall
            )
            if (saveStatus.error != null) {
                Text(
                    "模拟设置保存失败：${saveStatus.error}。当前预览已保留，尚未写入磁盘。",
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = flushSettings) { Text("重试保存") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("返回") }
                OutlinedButton(onClick = {
                    update {
                        it.copy(
                            width = it.height.coerceAtMost(1600),
                            height = it.width.coerceAtLeast(320)
                        )
                    }
                }) { Text("旋转") }
                OutlinedButton(onClick = { state.load(state.scenario) }) { Text("重置页面") }
            }
            ServiceSimulationControls(state)
            Text("视口", style = MaterialTheme.typography.titleMedium)
            GkControlSlider(
                "宽度 dp",
                environment.width,
                240..1200,
                onFinished = flushSettings
            ) { v -> update { it.copy(width = v) } }
            GkControlSlider(
                "高度 dp",
                environment.height,
                320..1400,
                onFinished = flushSettings
            ) { v -> update { it.copy(height = v) } }
            GkControlSlider(
                "显示倍率",
                environment.density,
                0.5f..3f,
                onFinished = flushSettings
            ) { v -> update { it.copy(density = v) } }
            GkControlSlider(
                "字体缩放",
                environment.fontScale,
                0.5f..3f,
                onFinished = flushSettings
            ) { v -> update { it.copy(fontScale = v) } }
            GkTextSwitch(
                title = "系统深色外观",
                checked = environment.dark,
                onCheckedChange = { v -> update { it.copy(dark = v) } })
            Text("系统栏", style = MaterialTheme.typography.titleMedium)
            GkTextSwitch(
                title = "状态栏",
                checked = android.statusBarVisible,
                onCheckedChange = { v -> system { it.copy(statusBarVisible = v) } })
            GkControlSlider(
                "状态栏高度",
                android.statusBarHeight.roundToInt(),
                0..100,
                onFinished = flushSettings
            ) { v -> system { it.copy(statusBarHeight = v.toFloat()) } }
            GkTextSwitch(
                title = "导航栏",
                checked = android.navigationBarVisible,
                onCheckedChange = { v -> system { it.copy(navigationBarVisible = v) } })
            GkControlSlider(
                "导航栏高度",
                android.navigationBarHeight.roundToInt(),
                0..100,
                onFinished = flushSettings
            ) { v -> system { it.copy(navigationBarHeight = v.toFloat()) } }
            GkTextSwitch(
                title = "手势条",
                checked = android.gestureHandleVisible,
                onCheckedChange = { v -> system { it.copy(gestureHandleVisible = v) } })
            GkTextSwitch(
                title = "充电",
                checked = android.charging,
                onCheckedChange = { v -> system { it.copy(charging = v) } })
            GkTextSwitch(
                title = "Wi-Fi",
                checked = android.wifi,
                onCheckedChange = { v -> system { it.copy(wifi = v) } })
            Text("输入法", style = MaterialTheme.typography.titleMedium)
            GkTextSwitch(
                title = "显示 IME 区域",
                checked = android.imeVisible,
                onCheckedChange = { v ->
                    system {
                        it.copy(
                            imeVisible = v,
                            imeHeight = it.imeHeight.coerceAtMost(environment.height - it.topInset - it.bottomInset - 1f)
                        )
                    }
                })
            GkControlSlider(
                "IME 高度",
                android.imeHeight.roundToInt(),
                100..minOf(
                    600f,
                    environment.height - android.topInset - android.bottomInset - 1f
                ).toInt(),
                onFinished = flushSettings
            ) { v -> system { it.copy(imeHeight = v.toFloat()) } }
            Text("平台状态注入", style = MaterialTheme.typography.titleMedium)
            GkTextSwitch(
                title = "读取应用列表权限（模拟）",
                checked = settings.permissions.canQueryPackages,
                onCheckedChange = { v ->
                    state.simulatorCommand {
                        state.simulator.update {
                            it.copy(
                                permissions = it.permissions.copy(
                                    canQueryPackages = v
                                )
                            )
                        }
                    }
                })
            GkTextSwitch(
                title = "应用列表权限异常（模拟）",
                checked = settings.permissions.queryPackagesAbnormal,
                onCheckedChange = { v ->
                    state.simulatorCommand {
                        state.simulator.update {
                            it.copy(
                                permissions = it.permissions.copy(
                                    queryPackagesAbnormal = v
                                )
                            )
                        }
                    }
                })
            GkTextSwitch(
                title = "特权服务（模拟）",
                checked = settings.privilege.available,
                onCheckedChange = { v ->
                    state.simulatorCommand {
                        state.simulator.setPrivilegeAvailable(v)
                    }
                })
            GkTextSwitch(
                title = "忽略电池优化（模拟）",
                checked = android.ignoreBatteryOptimizations,
                onCheckedChange = { v -> system { it.copy(ignoreBatteryOptimizations = v) } })
            GkTextSwitch(
                title = "安全设置写入权限（模拟）",
                checked = android.writeSecureSettings,
                onCheckedChange = { v -> system { it.copy(writeSecureSettings = v) } })
            GkTextSwitch(
                title = stringResource(Res.string.simulation_accessibility_enabled),
                checked = android.a11yEnabled,
                onCheckedChange = { v -> state.simulatorCommand {
                    DesktopRuntime.requireCurrent().services.setAccessibilityEnabled(v)
                } })
            GkTextSwitch(
                title = "局部禁用（模拟）",
                checked = android.partiallyDisabled,
                onCheckedChange = { v -> system { it.copy(partiallyDisabled = v) } })
            GkTextSwitch(
                title = "系统设置受限（模拟）",
                checked = settings.permissions.restricted,
                onCheckedChange = { v -> state.simulatorCommand { state.simulator.setRestricted(v) } })
            listOf(
                AndroidPermissions.GRANT_RUNTIME_PERMISSIONS,
                AndroidPermissions.INJECT_EVENTS,
                AndroidPermissions.WRITE_SECURE_SETTINGS,
                AndroidPermissions.MANAGE_APP_OPS_MODES,
            ).forEach { permission ->
                val name = permission.substringAfterLast('.')
                GkTextSwitch(
                    title = "ADB 缺少 $name（模拟）",
                    checked = permission in settings.permissions.deniedServerPermissions,
                    onCheckedChange = { denied ->
                        state.simulatorCommand {
                            state.simulator.update {
                                it.copy(permissions = it.permissions.copy(
                                    deniedServerPermissions = if (denied) it.permissions.deniedServerPermissions + permission
                                    else it.permissions.deniedServerPermissions - permission
                                ))
                            }
                        }
                    },
                )
            }
            GkTextSwitch(
                title = "受限设置提示（模拟）",
                checked = android.restrictedWarning,
                onCheckedChange = { v -> system { it.copy(restrictedWarning = v) } })
            GkTextSwitch(
                title = "自动化被占用（模拟）",
                checked = android.automationOccupied,
                onCheckedChange = { v -> system { it.copy(automationOccupied = v) } })
            OutlinedTextField(
                value = android.topAppId,
                onValueChange = { v -> system { it.copy(topAppId = v) } },
                label = { Text("前台应用包名（模拟）") })
            Text("应用流程（首次使用为预览）", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.overlay = "terms" }) { Text("首次使用") }
                OutlinedButton(onClick = { session.state.mainVm.updateStatus?.checkUpdate(true) }) { Text("更新") }
                OutlinedButton(onClick = session.state.mainVm.githubUpload::editCookie) { Text("Cookie") }
            }
            Text("当前路由：${state.scenario.page}")
            Text("回退栈：${state.mainVm.navigator.backStack.joinToString(" → ")}")
            GkTextSwitch(
                title = "本地网络权限",
                checked = android.localNetworkGranted,
                onCheckedChange = { v -> system { it.copy(localNetworkGranted = v) } })
            Text("本地归档", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = session.backupPath,
                onValueChange = { session.backupPath = it },
                label = { Text("备份文件路径") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { session.exportBackup() }) { Text("导出备份文件") }
                OutlinedButton(
                    onClick = { session.importBackup() },
                    enabled = session.backupPath.isNotBlank()
                ) { Text("导入备份文件") }
            }
            OutlinedButton(onClick = { session.exportLogs() }) { Text("导出日志文件") }
            Text(
                "数据目录：${DesktopStorage.data}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("HTTP：${DesktopDebugServer.address}")
        }
    }
}

@Composable
private fun GkControlSlider(
    label: String,
    value: Int,
    range: IntRange,
    onFinished: () -> Unit,
    onChange: (Int) -> Unit
) {
    val current = value.coerceIn(range)
    Column {
        Text("$label：$current")
        Slider(
            value = current.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            onValueChangeFinished = onFinished,
        )
    }
}

@Composable
private fun GkControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onFinished: () -> Unit,
    onChange: (Float) -> Unit
) {
    Column {
        Text("$label：${"%.2f".format(value)}")
        Slider(
            value = value.coerceIn(range),
            valueRange = range,
            onValueChange = onChange,
            onValueChangeFinished = onFinished
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceSimulationControls(state: DesktopState) {
    val settings by state.simulator.settings.collectAsStateWithLifecycle()
    val controller = DesktopRuntime.requireCurrent().services
    Text(stringResource(Res.string.simulation_services), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.simulation_services_description), style = MaterialTheme.typography.bodySmall)
    GkTextSwitch(
        title = stringResource(Res.string.permission_notifications),
        checked = settings.permissions.notificationGranted,
        onCheckedChange = { value -> state.simulatorCommand { state.simulator.update {
            it.copy(permissions = it.permissions.copy(notificationGranted = value))
        } } },
    )
    GkTextSwitch(
        title = stringResource(Res.string.permission_overlay),
        checked = settings.permissions.overlayGranted,
        onCheckedChange = { value -> state.simulatorCommand { state.simulator.update {
            it.copy(permissions = it.permissions.copy(overlayGranted = value))
        } } },
    )
    var selectedService by remember { mutableStateOf(SimulatedService.Status) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SimulatedService.entries.forEach { service ->
            FilterChip(selected = selectedService == service, onClick = { selectedService = service },
                label = { Text(stringResource(service.label())) })
        }
    }
    val service = selectedService
    val current = settings.services.state(service)
    fun event(type: ServiceEventType) = state.simulatorCommand {
        controller.event(ServiceEvent(service, type, current.attempt))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(stringResource(Res.string.simulation_service_status,
            stringResource(service.label()), stringResource(current.phase.label())))
        current.failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { state.simulatorCommand { controller.setEnabled(service, true) } }) {
                Text(stringResource(Res.string.simulation_start))
            }
            OutlinedButton(onClick = { state.simulatorCommand { controller.setEnabled(service, false) } }) {
                Text(stringResource(Res.string.action_stop))
            }
            if (current.phase == ServicePhase.Starting && service != SimulatedService.Http) {
                OutlinedButton(onClick = { event(ServiceEventType.Connected) }) { Text(stringResource(Res.string.simulation_complete)) }
                OutlinedButton(onClick = { event(ServiceEventType.Failed) }) { Text(stringResource(Res.string.simulation_failed)) }
            }
            if (current.phase == ServicePhase.AwaitingAuthorization) {
                OutlinedButton(onClick = { event(ServiceEventType.Authorized) }) { Text(stringResource(Res.string.action_agree)) }
                OutlinedButton(onClick = { event(ServiceEventType.Cancelled) }) { Text(stringResource(Res.string.action_cancel)) }
            }
            if (current.phase == ServicePhase.Running && service != SimulatedService.Http) {
                OutlinedButton(onClick = { event(ServiceEventType.Failed) }) { Text(stringResource(Res.string.simulation_terminate)) }
            }
        }
        if (service != SimulatedService.Http) {
            val plan = settings.services.plans.getValue(service)
            Text(stringResource(Res.string.simulation_start_result), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ServiceStartResult.entries.forEach { result ->
                    FilterChip(selected = plan.startResult == result,
                        onClick = { state.simulatorCommand { controller.configure(service, plan.copy(startResult = result)) } },
                        label = { Text(stringResource(when (result) {
                            ServiceStartResult.Success -> Res.string.simulation_success
                            ServiceStartResult.Failure -> Res.string.simulation_failed
                            ServiceStartResult.Manual -> Res.string.simulation_manual
                        })) })
                }
            }
            if (service == SimulatedService.Screenshot || service == SimulatedService.Accessibility) {
                Text(stringResource(Res.string.simulation_authorization_result), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ServiceAuthorization.entries.forEach { authorization ->
                        FilterChip(selected = plan.authorization == authorization,
                            onClick = { state.simulatorCommand { controller.configure(service, plan.copy(authorization = authorization)) } },
                            label = { Text(stringResource(when (authorization) {
                                ServiceAuthorization.Allow -> Res.string.action_agree
                                ServiceAuthorization.Cancel -> Res.string.action_cancel
                                ServiceAuthorization.Manual -> Res.string.simulation_authorization_dialog
                            })) })
                    }
                }
            }
        }
    }
    settings.services.logs.takeLast(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
}
