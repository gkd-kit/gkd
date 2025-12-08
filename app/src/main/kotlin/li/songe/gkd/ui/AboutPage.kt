package li.songe.gkd.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.app
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.RotatingLoadingIcon
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextMenu
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.ISSUES_URL
import li.songe.gkd.util.PLAY_STORE_URL
import li.songe.gkd.util.REPOSITORY_URL
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.UpdateChannelOption
import li.songe.gkd.util.buildLogFile
import li.songe.gkd.util.findOption
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.openUri
import li.songe.gkd.util.saveFileToDownloads
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.sharedDir
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import java.io.File

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AboutVm>()
    val store by storeFlow.collectAsState()

    var showInfoDlg by vm.showInfoDlgFlow.asMutableState()
    if (showInfoDlg) {
        AlertDialog(
            onDismissRequest = { showInfoDlg = false },
            title = { Text(text = "版本信息") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = "构建渠道")
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = "版本代码")
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = "版本名称")
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = "代码记录")
                        Text(
                            modifier = Modifier.clickable { openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = "提交时间")
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInfoDlg = false
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
    var showShareLogDlg by vm.showShareLogDlgFlow.asMutableState()
    var showShareAppDlg by vm.showShareAppDlgFlow.asMutableState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = {
                            mainVm.popBackStack()
                        },
                    )
                },
                title = { Text(text = "关于") },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Share,
                        onClick = {
                            showShareAppDlg = true
                        },
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLogoIcon(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = throttle { toast("你干嘛~ 哎呦~") }
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = { showInfoDlg = true })
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = META.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = META.versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            SettingItem(
                imageVector = null,
                title = "开源代码",
                onClick = {
                    mainVm.openUrl(REPOSITORY_URL)
                },
            )
            if (META.isGkdChannel) {
                SettingItem(
                    imageVector = null,
                    title = "捐赠支持",
                    onClick = {
                        mainVm.navigateWebPage(ShortUrlSet.URL10)
                    },
                )
            }
            SettingItem(
                imageVector = null,
                title = "使用协议",
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL12)
                },
            )
            SettingItem(
                imageVector = null,
                title = "隐私政策",
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL11)
                },
            )

            Text(
                text = "反馈",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .clickable(onClick = throttle(mainVm.viewModelScope.launchAsFn {
                        mainVm.dialogFlow.waitResult(
                            title = "反馈须知",
                            textContent = {
                                Text(text = buildAnnotatedString {
                                    val highlightStyle = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    append("感谢您愿意花时间反馈，")
                                    withStyle(style = highlightStyle) {
                                        append("GKD 默认不携带任何规则，只接受应用本体功能相关的反馈")
                                    }
                                    append("\n\n")
                                    append("请先判断是不是第三方规则订阅的问题，如果是，您应该向规则提供者反馈，而不是在此处反馈。")
                                    withStyle(style = highlightStyle) {
                                        append("如果您已经确信是 GKD 应用本体的问题")
                                    }
                                    append("，可点击下方继续反馈")
                                })
                            },
                            confirmText = "继续",
                            dismissRequest = true,
                        )
                        mainVm.openUrl(ISSUES_URL)
                    }))
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "问题反馈",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            SettingItem(
                title = "导出日志",
                imageVector = PerfIcon.Share,
                onClick = {
                    showShareLogDlg = true
                }
            )
            if (mainVm.updateStatus != null) {
                Text(
                    text = "更新",
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextMenu(
                    title = "更新渠道",
                    option = UpdateChannelOption.objects.findOption(store.updateChannel)
                ) {
                    if (mainVm.updateStatus.checkUpdatingFlow.value) return@TextMenu
                    if (it.value == UpdateChannelOption.Beta.value) {
                        mainVm.viewModelScope.launchTry {
                            mainVm.dialogFlow.waitResult(
                                title = "版本渠道",
                                text = "测试版本渠道更新快\n但不稳定可能存在较多BUG\n请谨慎使用",
                            )
                            storeFlow.update { s -> s.copy(updateChannel = it.value) }
                        }
                    } else {
                        storeFlow.update { s -> s.copy(updateChannel = it.value) }
                    }
                }
                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = throttle {
                                mainVm.updateStatus.checkUpdate(true)
                            }
                        )
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "检查更新",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RotatingLoadingIcon(loading = mainVm.updateStatus.checkUpdatingFlow.collectAsState().value)
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    if (showShareLogDlg) {
        Dialog(onDismissRequest = { showShareLogDlg = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = "分享到其他应用", modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.shareFile(logZipFile, "分享日志文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "保存到下载", modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.saveFileToDownloads(logZipFile)
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "生成链接(需科学上网)",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            mainVm.uploadOptions.startTask(
                                getFile = { buildLogFile() }
                            )
                        })
                        .then(modifier)
                )
            }
        }
    }

    if (showShareAppDlg) {
        Dialog(onDismissRequest = { showShareAppDlg = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = "分享到其他应用", modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareAppDlg = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                if (!META.isGkdChannel) {
                                    mainVm.dialogFlow.waitResult(
                                        title = "分享提示",
                                        textContent = { Text(text = exportPlayTipTemplate()) },
                                        confirmText = "继续",
                                    )
                                }
                                context.shareFile(getShareApkFile(), "分享安装文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "保存到下载", modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareAppDlg = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                if (!META.isGkdChannel) {
                                    mainVm.dialogFlow.waitResult(
                                        title = "保存提示",
                                        textContent = { Text(text = exportPlayTipTemplate()) },
                                        confirmText = "继续",
                                    )
                                }
                                context.saveFileToDownloads(getShareApkFile())
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "Google Play", modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareAppDlg = false
                            mainVm.openUrl(PLAY_STORE_URL)
                        })
                        .then(modifier)
                )
            }
        }
    }
}

@Composable
private fun exportPlayTipTemplate(): AnnotatedString {
    return buildAnnotatedString {
        append("当前导出的 APK 文件只能在已安装 Google 框架的设备上才能使用，否则安装打开后会提示报错，")
        withLink(
            LinkAnnotation.Url(
                ShortUrlSet.URL13,
                TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
            )
        ) {
            append("建议点此从官网下载")
        }
        append("，或点击下方继续操作")
    }
}

private fun getShareApkFile(): File {
    return sharedDir.resolve("gkd-v${META.versionName}.apk").apply {
        File(app.packageCodePath).copyTo(this, overwrite = true)
    }
}

@Composable
private fun AnimatedLogoIcon(
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalDarkTheme.current
    val colorRid = if (darkTheme) R.color.better_white else R.color.better_black
    var atEnd by remember { mutableStateOf(false) }
    val animation = AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_anim_logo)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd
    )
    LaunchedEffect(Unit) {
        while (isActive) {
            atEnd = !atEnd
            delay(animation.totalDuration.toLong())
        }
    }
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = colorResource(colorRid),
    )
}