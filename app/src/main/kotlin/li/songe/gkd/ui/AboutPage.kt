package li.songe.gkd.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.ui.component.RotatingLoadingIcon
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextMenu
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.gkd.ui.local.LocalNavController
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.ISSUES_URL
import li.songe.gkd.util.PLAY_STORE_URL
import li.songe.gkd.util.REPOSITORY_URL
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.UpdateChannelOption
import li.songe.gkd.util.buildLogFile
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.findOption
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.openUri
import li.songe.gkd.util.saveFileToDownloads
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.sharedDir
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import java.io.File

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val navController = LocalNavController.current
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val store by storeFlow.collectAsState()

    var showInfoDlg by remember { mutableStateOf(false) }
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
                            text = META.commitId.substring(0, 16),
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
    var showShareLogDlg by remember { mutableStateOf(false) }
    var showShareAppDlg by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                title = { Text(text = "关于") },
                actions = {
                    IconButton(onClick = {
                        showShareAppDlg = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                        )
                    }
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

            Column(
                modifier = Modifier
                    .clickable(onClick = throttle {
                        mainVm.openUrl(REPOSITORY_URL)
                    })
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "开源代码",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Column(
                modifier = Modifier
                    .clickable(onClick = throttle {
                        mainVm.navigatePage(WebViewPageDestination(ShortUrlSet.URL11))
                    })
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "隐私政策",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = "反馈",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .clickable(onClick = throttle {
                        mainVm.openUrl(ISSUES_URL)
                    })
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
                imageVector = Icons.Default.Share,
                onClick = {
                    showShareLogDlg = true
                }
            )
            if (META.updateEnabled) {
                val checkUpdating by mainVm.updateStatus.checkUpdatingFlow.collectAsState()
                Text(
                    text = "更新",
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextSwitch(
                    title = "自动更新",
                    checked = store.autoCheckAppUpdate,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            autoCheckAppUpdate = it
                        )
                    }
                )
                TextMenu(
                    title = "更新渠道",
                    option = UpdateChannelOption.allSubObject.findOption(store.updateChannel)
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
                            onClick = throttle(fn = mainVm.viewModelScope.launchAsFn {
                                if (mainVm.updateStatus.checkUpdatingFlow.value) return@launchAsFn
                                val newVersion = mainVm.updateStatus.checkUpdate()
                                if (newVersion == null) {
                                    toast("暂无更新")
                                }
                            })
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
                    RotatingLoadingIcon(loading = checkUpdating)
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
                                val apkFile = sharedDir.resolve("gkd-v${META.versionName}.apk")
                                File(app.packageCodePath).copyTo(apkFile, overwrite = true)
                                context.shareFile(apkFile, "分享安装文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "保存到下载", modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareAppDlg = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val apkFile = sharedDir.resolve("gkd-v${META.versionName}.apk")
                                File(app.packageCodePath).copyTo(apkFile, overwrite = true)
                                context.saveFileToDownloads(apkFile)
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
private fun AnimatedLogoIcon(
    modifier: Modifier = Modifier
) {
    val mainVm = LocalMainViewModel.current
    val enableDarkTheme by mainVm.enableDarkThemeFlow.collectAsState()
    val darkTheme = enableDarkTheme ?: isSystemInDarkTheme()
    var atEnd by remember { mutableStateOf(false) }
    val animation = AnimatedImageVector.animatedVectorResource(id = SafeR.ic_anim_logo)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd
    )
    LaunchedEffect(Unit) {
        while (true) {
            atEnd = !atEnd
            delay(animation.totalDuration.toLong())
        }
    }
    val colorRid = if (darkTheme) SafeR.better_white else SafeR.better_black
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = colorResource(colorRid),
    )
}