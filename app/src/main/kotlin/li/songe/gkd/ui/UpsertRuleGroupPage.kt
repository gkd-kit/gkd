package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.KeyboardUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SubsAppGroupListPageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsGlobalGroupListPageDestination
import com.ramcosta.composedestinations.generated.destinations.UpsertRuleGroupPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.MainActivity
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.local.LocalDarkTheme
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.gkd.ui.local.LocalNavController
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.clearJson5TransformationCache
import li.songe.gkd.ui.style.getJson5Transformation
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle

@Suppress("unused")
@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun UpsertRuleGroupPage(
    subsId: Long,
    groupKey: Int? = null,
    appId: String? = null,
    forward: Boolean = false,
) {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<UpsertRuleGroupVm>()
    val text by vm.textFlow.collectAsState()
    fun checkIfSaveText() = mainVm.viewModelScope.launchTry(Dispatchers.Default) {
        if (vm.textChanged) {
            mainVm.dialogFlow.waitResult(
                title = "放弃编辑",
                text = "当前内容未保存，是否放弃编辑？",
            )
        }
        withContext(Dispatchers.Main) { mainVm.navController.popBackStack() }
    }.let { }

    val onClickSave = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
        vm.saveRule()
        if (KeyboardUtils.isSoftInputVisible(context)) {
            KeyboardUtils.hideSoftInput(context)
        }
        withContext(Dispatchers.Main) {
            if (forward) {
                if (appId == null) {
                    navController.navigate(SubsGlobalGroupListPageDestination(subsItemId = subsId).route) {
                        popUpTo(UpsertRuleGroupPageDestination.route) {
                            inclusive = true
                        }
                    }
                } else {
                    navController.navigate(
                        SubsAppGroupListPageDestination(
                            subsItemId = subsId,
                            vm.addAppId ?: appId
                        ).route
                    ) {
                        popUpTo(UpsertRuleGroupPageDestination.route) {
                            inclusive = true
                        }
                    }
                }
            } else {
                navController.popBackStack()
            }
        }
    })
    BackHandler(true) {
        if (KeyboardUtils.isSoftInputVisible(context)) {
            KeyboardUtils.hideSoftInput(context)
            return@BackHandler
        }
        checkIfSaveText()
    }
    DisposableEffect(null) {
        onDispose {
            clearJson5TransformationCache()
        }
    }
    Scaffold(modifier = Modifier, topBar = {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = throttle {
                    if (KeyboardUtils.isSoftInputVisible(context)) {
                        KeyboardUtils.hideSoftInput(context)
                    }
                    checkIfSaveText()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = {
                Text(text = if (vm.isEdit) "编辑规则组" else "添加规则组")
            },
            actions = {
                IconButton(onClick = onClickSave, enabled = text.isNotBlank()) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                }
            }
        )
    }) { paddingValues ->
        val textColors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
        Box(
            modifier = Modifier
                .scaffoldPadding(paddingValues)
                .fillMaxSize(),
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                // need compose 1.9.0
                TextField(
                    value = text,
                    onValueChange = { vm.textFlow.value = it },
                    modifier = Modifier
                        .autoFocus()
                        .fillMaxSize()
                        .imePadding(),
                    shape = RectangleShape,
                    colors = textColors,
                    visualTransformation = getJson5Transformation(LocalDarkTheme.current),
                    placeholder = {
                        Text(text = if (vm.isApp) "请输入应用规则组\n" else "请输入全局规则组\n")
                    },
                )
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text.length.toString(),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

