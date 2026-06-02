package li.songe.gkd.ui.student

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.ui.AuthA11yRoute
import li.songe.gkd.ui.WebViewRoute
import li.songe.gkd.ui.component.AppIcon
import li.songe.gkd.ui.component.CustomOutlinedTextField
import li.songe.gkd.ui.component.PerfCheckbox
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle

@Serializable
data object StudentOnboardingRoute : NavKey

private const val STEP_PERMISSION = 0
private const val STEP_SUBSCRIPTION = 1
private const val STEP_APPS = 2
private const val STEP_COMPLETE = 3
private const val STEP_COUNT = 4

private val stepTitles = listOf("权限", "订阅", "选择 App", "完成")

@Composable
fun StudentOnboardingPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<StudentOnboardingVm>()
    val step = rememberSaveable { mutableIntStateOf(STEP_PERMISSION) }

    val canQueryPackages by vm.canQueryPackagesFlow.collectAsState()
    val notificationGranted by vm.notificationGrantedFlow.collectAsState()
    val writeSecureSettingsGranted by vm.writeSecureSettingsGrantedFlow.collectAsState()
    val appOpsRestricted by vm.appOpsRestrictedFlow.collectAsState()
    val recommendedReady by vm.recommendedSubscriptionReadyFlow.collectAsState()
    val candidates by vm.candidatesFlow.collectAsState()
    val selectedCount by vm.selectedCountFlow.collectAsState()
    val canApply by vm.canApplyFlow.collectAsState()
    val isImporting by vm.isImportingSubscriptionFlow.collectAsState()
    val isApplying by vm.isApplyingFlow.collectAsState()
    val completed by vm.completedFlow.collectAsState()
    val searchText by vm.searchStrFlow.collectAsState()

    LaunchedEffect(completed) {
        if (completed) {
            step.intValue = STEP_COMPLETE
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
                },
                title = { Text(text = "学生入门") },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "查看学生入门说明",
                        onClick = {
                            mainVm.navigatePage(WebViewRoute(initUrl = STUDENT_GUIDE_URL))
                        },
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
        ) {
            StudentStepHeader(step = step.intValue)
            when (step.intValue) {
                STEP_PERMISSION -> {
                    StudentPermissionStep(
                        modifier = Modifier.weight(1f),
                        canQueryPackages = canQueryPackages,
                        notificationGranted = notificationGranted,
                        writeSecureSettingsGranted = writeSecureSettingsGranted,
                        appOpsRestricted = appOpsRestricted,
                        onRequestQueryPackages = mainVm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            requiredPermission(context, canQueryPkgState)
                        },
                        onOpenWorkMode = {
                            mainVm.navigatePage(AuthA11yRoute)
                        },
                    )
                    StudentStepActions(
                        canBack = false,
                        canNext = true,
                        nextText = "继续",
                        onBack = {},
                        onNext = { step.intValue = STEP_SUBSCRIPTION },
                    )
                }

                STEP_SUBSCRIPTION -> {
                    StudentSubscriptionStep(
                        modifier = Modifier.weight(1f),
                        recommendedReady = recommendedReady,
                        isImporting = isImporting,
                        onImport = {
                            vm.addRecommendedSubscription(mainVm)
                        },
                        onOpenSource = {
                            mainVm.navigatePage(
                                WebViewRoute(initUrl = STUDENT_RECOMMENDED_SUBSCRIPTION_SOURCE)
                            )
                        },
                    )
                    StudentStepActions(
                        canBack = true,
                        canNext = recommendedReady,
                        nextText = "选择 App",
                        onBack = { step.intValue = STEP_PERMISSION },
                        onNext = { step.intValue = STEP_APPS },
                    )
                }

                STEP_APPS -> {
                    StudentAppsStep(
                        modifier = Modifier.weight(1f),
                        canQueryPackages = canQueryPackages,
                        recommendedReady = recommendedReady,
                        searchText = searchText,
                        selectedCount = selectedCount,
                        candidates = candidates,
                        onSearchTextChange = { vm.searchStrFlow.value = it },
                        onToggleCandidate = { candidate ->
                            vm.setAppSelected(candidate.appId, !candidate.selected)
                        },
                        onClearSelection = vm::clearSelectedApps,
                        onRequestQueryPackages = mainVm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            requiredPermission(context, canQueryPkgState)
                        },
                    )
                    StudentStepActions(
                        canBack = true,
                        canNext = canApply,
                        nextText = if (isApplying) "应用中" else "应用选择",
                        onBack = { step.intValue = STEP_SUBSCRIPTION },
                        onNext = vm::applySelectedPlan,
                    )
                }

                else -> {
                    StudentCompleteStep(
                        modifier = Modifier.weight(1f),
                        onBackHome = { mainVm.popPage() },
                        onOpenGuide = {
                            mainVm.navigatePage(WebViewRoute(initUrl = STUDENT_GUIDE_URL))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentStepHeader(step: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "步骤 ${step + 1}/$STEP_COUNT · ${stepTitles[step.coerceIn(0, stepTitles.lastIndex)]}",
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = { (step + 1).toFloat() / STEP_COUNT },
        )
    }
}

@Composable
private fun StudentPermissionStep(
    modifier: Modifier,
    canQueryPackages: Boolean,
    notificationGranted: Boolean,
    writeSecureSettingsGranted: Boolean,
    appOpsRestricted: Boolean,
    onRequestQueryPackages: () -> Unit,
    onOpenWorkMode: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudentInfoCard(
            title = "准备基础权限",
            text = "应用列表权限用于只展示你已安装且订阅支持的 App；工作模式权限用于实际执行跳过操作。",
        )
        StudentStatusCard {
            StudentStatusRow(title = "读取应用列表", ready = canQueryPackages)
            StudentStatusRow(title = "通知权限", ready = notificationGranted)
            StudentStatusRow(title = "增强授权", ready = writeSecureSettingsGranted)
            StudentStatusRow(title = "受限设置", ready = !appOpsRestricted)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = !canQueryPackages,
                onClick = throttle(onRequestQueryPackages),
            ) {
                Text(text = if (canQueryPackages) "已授权列表" else "授权列表")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = throttle(onOpenWorkMode),
            ) {
                Text(text = "工作模式")
            }
        }
        Spacer(modifier = Modifier.height(EmptyHeight))
    }
}

@Composable
private fun StudentSubscriptionStep(
    modifier: Modifier,
    recommendedReady: Boolean,
    isImporting: Boolean,
    onImport: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudentInfoCard(
            title = "导入推荐订阅",
            text = "推荐订阅来自上游社区维护者，导入后仍会让你逐个选择要开启的 App。",
        )
        StudentStatusCard {
            StudentStatusRow(title = STUDENT_RECOMMENDED_SUBSCRIPTION_NAME, ready = recommendedReady)
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = STUDENT_RECOMMENDED_SUBSCRIPTION_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isImporting,
                onClick = throttle(onImport),
            ) {
                Text(text = if (isImporting) "导入中" else if (recommendedReady) "更新订阅" else "导入订阅")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = throttle(onOpenSource),
            ) {
                Text(text = "查看来源")
            }
        }
        Spacer(modifier = Modifier.height(EmptyHeight))
    }
}

@Composable
private fun StudentAppsStep(
    modifier: Modifier,
    canQueryPackages: Boolean,
    recommendedReady: Boolean,
    searchText: String,
    selectedCount: Int,
    candidates: List<StudentAppCandidate>,
    onSearchTextChange: (String) -> Unit,
    onToggleCandidate: (StudentAppCandidate) -> Unit,
    onClearSelection: () -> Unit,
    onRequestQueryPackages: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomOutlinedTextField(
                modifier = Modifier.weight(1f),
                value = searchText,
                onValueChange = onSearchTextChange,
                singleLine = true,
                placeholder = { Text(text = "搜索 App 或包名") },
            )
            TextButton(
                enabled = selectedCount > 0,
                onClick = throttle(onClearSelection),
            ) {
                Text(text = "清空")
            }
        }
        Text(
            text = "已选择 $selectedCount 个 App",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            !canQueryPackages -> {
                StudentEmptyState(
                    modifier = Modifier.weight(1f),
                    text = "需要应用列表权限来匹配你已安装的学校常用 App。",
                    actionText = "授权列表",
                    onAction = onRequestQueryPackages,
                )
            }

            !recommendedReady -> {
                StudentEmptyState(
                    modifier = Modifier.weight(1f),
                    text = "请先导入推荐订阅。",
                )
            }

            candidates.isEmpty() -> {
                StudentEmptyState(
                    modifier = Modifier.weight(1f),
                    text = "当前没有匹配的已安装 App。",
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = candidates,
                        key = { candidate -> candidate.appId },
                    ) { candidate ->
                        StudentCandidateCard(
                            candidate = candidate,
                            onToggle = { onToggleCandidate(candidate) },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(EmptyHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCompleteStep(
    modifier: Modifier,
    onBackHome: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        PerfIcon(
            imageVector = PerfIcon.Check,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "学生入门已完成",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "后续你仍然可以在订阅管理里调整规则，或在 App 配置页单独关闭某个学校 App。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = throttle(onBackHome)) {
            Text(text = "返回首页")
        }
        TextButton(onClick = throttle(onOpenGuide)) {
            Text(text = "查看说明")
        }
        Spacer(modifier = Modifier.height(EmptyHeight))
    }
}

@Composable
private fun StudentInfoCard(
    title: String,
    text: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = surfaceCardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudentStatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = surfaceCardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun StudentStatusRow(
    title: String,
    ready: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PerfIcon(
            imageVector = if (ready) PerfIcon.Check else PerfIcon.WarningAmber,
            modifier = Modifier.size(20.dp),
            tint = if (ready) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            contentDescription = null,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (ready) "已就绪" else "待处理",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StudentCandidateCard(
    candidate: StudentAppCandidate,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = throttle(onToggle)),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(appId = candidate.appId)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = candidate.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${candidate.groupCount} 个规则组 · ${candidate.appId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PerfCheckbox(
                key = candidate.appId,
                checked = candidate.selected,
            )
        }
    }
}

@Composable
private fun StudentEmptyState(
    modifier: Modifier,
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PerfIcon(
            imageVector = PerfIcon.Info,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Button(onClick = throttle(onAction)) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
private fun StudentStepActions(
    canBack: Boolean,
    canNext: Boolean,
    nextText: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canBack) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = throttle(onBack),
            ) {
                Text(text = "上一步")
            }
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = canNext,
            onClick = throttle(onNext),
        ) {
            Text(text = nextText)
        }
    }
}
