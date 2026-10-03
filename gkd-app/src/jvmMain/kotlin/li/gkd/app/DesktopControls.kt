package li.gkd.app

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
                title = "无障碍运行（模拟）",
                checked = android.serviceEnabled,
                onCheckedChange = { v -> system { it.copy(serviceEnabled = v) } })
            GkTextSwitch(
                title = "特权服务（模拟）",
                checked = settings.privilege.available,
                onCheckedChange = { v ->
                    state.simulatorCommand {
                        state.simulator.setPrivilegeAvailable(v)
                    }
                })
            GkTextSwitch(
                title = "常驻通知（模拟）",
                checked = android.statusEnabled,
                onCheckedChange = { v -> system { it.copy(statusEnabled = v) } })
            GkTextSwitch(
                title = "忽略电池优化（模拟）",
                checked = android.ignoreBatteryOptimizations,
                onCheckedChange = { v -> system { it.copy(ignoreBatteryOptimizations = v) } })
            GkTextSwitch(
                title = "安全设置写入权限（模拟）",
                checked = android.writeSecureSettings,
                onCheckedChange = { v -> system { it.copy(writeSecureSettings = v) } })
            GkTextSwitch(
                title = "自动化运行（模拟）",
                checked = android.automationRunning,
                onCheckedChange = { v -> system { it.copy(automationRunning = v) } })
            GkTextSwitch(
                title = "无障碍已启用但未连接（模拟）",
                checked = android.a11yEnabled,
                onCheckedChange = { v -> system { it.copy(a11yEnabled = v) } })
            GkTextSwitch(
                title = "局部禁用（模拟）",
                checked = android.partiallyDisabled,
                onCheckedChange = { v -> system { it.copy(partiallyDisabled = v) } })
            GkTextSwitch(
                title = "活动记录服务（模拟）",
                checked = android.activityRunning,
                onCheckedChange = { v -> system { it.copy(activityRunning = v) } })
            GkTextSwitch(
                title = "系统设置受限（模拟）",
                checked = settings.permissions.restricted,
                onCheckedChange = { v -> state.simulatorCommand { state.simulator.setRestricted(v) } })
            listOf(
                AndroidPermissions.GRANT_RUNTIME_PERMISSIONS,
                AndroidPermissions.INJECT_EVENTS,
                AndroidPermissions.WRITE_SECURE_SETTINGS,
                AndroidPermissions.UPDATE_APP_OPS_STATS,
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
                OutlinedButton(onClick = { session.updateStatus.checkUpdate(true) }) { Text("更新") }
                OutlinedButton(onClick = session.githubUpload::editCookie) { Text("Cookie") }
            }
            Text("当前路由：${state.scenario.page}")
            Text("回退栈：${state.backStack.joinToString(" → ")}")
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
                    onClick = { session.importBackup(selectFile = false) },
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
