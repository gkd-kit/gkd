package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.getJson5Transformation
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle

@Serializable
data class UpsertRuleGroupRoute(
    val subsId: Long,
    val groupKey: Int? = null,
    val appId: String? = null,
    val forward: Boolean = false,
) : NavKey

@Composable
fun UpsertRuleGroupPage(route: UpsertRuleGroupRoute) {
    val subsId = route.subsId
    val appId = route.appId
    val forward = route.forward

    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { UpsertRuleGroupVm(route) }
    val text by vm.textFlow.collectAsState()

    val checkIfSaveText = throttle(mainVm.viewModelScope.launchAsFn(Dispatchers.Default) {
        if (vm.hasTextChanged()) {
            context.justHideSoftInput()
            mainVm.dialogFlow.waitResult(
                title = "提示",
                text = "当前内容未保存，是否放弃编辑？",
            )
        } else {
            context.hideSoftInput()
        }
        mainVm.popPage()
    })

    val onClickSave = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Main) {
        withContext(Dispatchers.Default) { vm.saveRule() }
        context.hideSoftInput()
        if (forward) {
            if (appId == null) {
                mainVm.navigatePage(
                    SubsGlobalGroupListRoute(subsItemId = subsId),
                    replaced = true
                )
            } else {
                mainVm.navigatePage(
                    SubsAppGroupListRoute(
                        subsItemId = subsId,
                        vm.addAppId ?: appId
                    ),
                    replaced = true
                )
            }
        } else {
            mainVm.popPage()
        }
    })
    BackHandler(true, checkIfSaveText)
    Scaffold(modifier = Modifier, topBar = {
        PerfTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = checkIfSaveText)
            },
            title = {
                Text(text = if (vm.isEdit) "编辑规则组" else "添加规则组")
            },
            actions = {
                PerfIconButton(
                    imageVector = PerfIcon.Save,
                    onClick = onClickSave,
                    enabled = text.isNotBlank()
                )
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
                val imeShowing by context.imePlayingFlow.collectAsState()
                val modifier = Modifier
                    .autoFocus()
                    .fillMaxSize()
                    .run {
                        if (imeShowing) {
                            this
                        } else {
                            imePadding()
                        }
                    }
                TextField(
                    value = text,
                    onValueChange = { vm.textFlow.value = it },
                    modifier = modifier,
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

