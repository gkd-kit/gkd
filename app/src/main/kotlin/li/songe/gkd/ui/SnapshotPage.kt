package li.songe.gkd.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.UriUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.permission.canSaveToAlbumState
import li.songe.gkd.permission.checkOrRequestPermission
import li.songe.gkd.ui.component.StartEllipsisText
import li.songe.gkd.ui.destinations.ImagePreviewPageDestination
import li.songe.gkd.util.IMPORT_BASE_URL
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.LocalPickContentLauncher
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.navigate
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.snapshotZipDir
import li.songe.gkd.util.toast

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SnapshotPage() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current as ComponentActivity
    val navController = LocalNavController.current
    val colorScheme = MaterialTheme.colorScheme

    val pickContentLauncher = LocalPickContentLauncher.current

    val vm = hiltViewModel<SnapshotVm>()
    val snapshots by vm.snapshotsState.collectAsState()
    val uploadStatus by vm.uploadStatusFlow.collectAsState()

    var selectedSnapshot by remember {
        mutableStateOf<Snapshot?>(null)
    }

    var showDeleteDlg by remember {
        mutableStateOf(false)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior,
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
            title = { Text(text = if (snapshots.isEmpty()) "快照记录" else "快照记录-${snapshots.size}") },
            actions = {
                if (snapshots.isNotEmpty()) {
                    IconButton(onClick = { showDeleteDlg = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            items(snapshots, { it.id }) { snapshot ->
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedSnapshot = snapshot
                    }
                    .padding(10.dp)) {
                    Row {
                        Text(
                            text = snapshot.date,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = snapshot.appName ?: snapshot.appId ?: snapshot.id.toString(),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )
                    }
                    if (snapshot.activityId != null) {
                        val showActivityId =
                            if (snapshot.appId != null && snapshot.activityId.startsWith(
                                    snapshot.appId
                                )
                            ) {
                                snapshot.activityId.substring(snapshot.appId.length)
                            } else {
                                snapshot.activityId
                            }
                        Spacer(modifier = Modifier.width(10.dp))
                        StartEllipsisText(text = showActivityId)
                    }
                }
                HorizontalDivider()
            }
            item {
                Spacer(modifier = Modifier.height(40.dp))
                if (snapshots.isEmpty()) {
                    Text(
                        text = "暂无记录",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

    })

    selectedSnapshot?.let { snapshotVal ->
        Dialog(onDismissRequest = { selectedSnapshot = null }) {
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
                    text = "查看", modifier = Modifier
                        .clickable(onClick = scope.launchAsFn {
                            navController.navigate(
                                ImagePreviewPageDestination(
                                    filePath = snapshotVal.screenshotFile.absolutePath,
                                    title = snapshotVal.appName,
                                )
                            )
                            selectedSnapshot = null
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "分享",
                    modifier = Modifier
                        .clickable(onClick = vm.viewModelScope.launchAsFn {
                            val zipFile =
                                SnapshotExt.getSnapshotZipFile(
                                    snapshotVal.id,
                                    snapshotVal.appId,
                                    snapshotVal.activityId
                                )
                            context.shareFile(zipFile, "分享快照文件")
                            selectedSnapshot = null
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                if (snapshotVal.githubAssetId != null) {
                    Text(
                        text = "复制链接", modifier = Modifier
                            .clickable(onClick = {
                                selectedSnapshot = null
                                ClipboardUtils.copyText(IMPORT_BASE_URL + snapshotVal.githubAssetId)
                                toast("复制成功")
                            })
                            .then(modifier)
                    )
                } else {
                    Text(
                        text = "生成链接(需科学上网)", modifier = Modifier
                            .clickable(onClick = {
                                selectedSnapshot = null
                                vm.uploadZip(snapshotVal)
                            })
                            .then(modifier)
                    )
                }
                HorizontalDivider()

                Text(
                    text = "保存截图到相册",
                    modifier = Modifier
                        .clickable(onClick = vm.viewModelScope.launchAsFn {
                            if (!checkOrRequestPermission(context, canSaveToAlbumState)) {
                                return@launchAsFn
                            }
                            ImageUtils.save2Album(
                                ImageUtils.getBitmap(snapshotVal.screenshotFile),
                                Bitmap.CompressFormat.PNG,
                                true
                            )
                            toast("保存成功")
                            selectedSnapshot = null
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "替换截图(去除隐私)",
                    modifier = Modifier
                        .clickable(onClick = vm.viewModelScope.launchAsFn {
                            val uri = pickContentLauncher.launchForImageResult()
                            withContext(Dispatchers.IO) {
                                val oldBitmap = ImageUtils.getBitmap(snapshotVal.screenshotFile)
                                val newBytes = UriUtils.uri2Bytes(uri)
                                val newBitmap = ImageUtils.getBitmap(newBytes, 0)
                                if (oldBitmap.width == newBitmap.width && oldBitmap.height == newBitmap.height) {
                                    snapshotVal.screenshotFile.writeBytes(newBytes)
                                    snapshotZipDir
                                        .listFiles { f -> f.isFile && f.name.endsWith("${snapshotVal.id}.zip") }
                                        ?.forEach { f ->
                                            f.delete()
                                        }
                                    if (snapshotVal.githubAssetId != null) {
                                        // 当本地快照变更时, 移除快照链接
                                        DbSet.snapshotDao.deleteGithubAssetId(snapshotVal.id)
                                    }
                                } else {
                                    toast("截图尺寸不一致,无法替换")
                                    return@withContext
                                }
                            }
                            toast("替换成功")
                            selectedSnapshot = null
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "删除", modifier = Modifier
                        .clickable(onClick = scope.launchAsFn {
                            DbSet.snapshotDao.delete(snapshotVal)
                            withContext(Dispatchers.IO) {
                                SnapshotExt.removeAssets(snapshotVal.id)
                            }
                            selectedSnapshot = null
                        })
                        .then(modifier), color = colorScheme.error
                )
            }
        }
    }

    when (val uploadStatusVal = uploadStatus) {
        is LoadStatus.Failure -> {
            AlertDialog(
                title = { Text(text = "上传失败") },
                text = {
                    Text(text = uploadStatusVal.exception.let {
                        it.message ?: it.toString()
                    })
                },
                onDismissRequest = { vm.uploadStatusFlow.value = null },
                confirmButton = {
                    TextButton(onClick = {
                        vm.uploadStatusFlow.value = null
                    }) {
                        Text(text = "关闭")
                    }
                },
            )
        }

        is LoadStatus.Loading -> {
            AlertDialog(
                title = { Text(text = "上传文件中") },
                text = {
                    LinearProgressIndicator(
                        progress = { uploadStatusVal.progress },
                    )
                },
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = {
                        vm.uploadJob?.cancel(CancellationException("终止上传"))
                        vm.uploadJob = null
                    }) {
                        Text(text = "终止上传")
                    }
                },
            )
        }

        is LoadStatus.Success -> {
            AlertDialog(title = { Text(text = "上传完成") }, text = {
                Text(text = IMPORT_BASE_URL + uploadStatusVal.result.id)
            }, onDismissRequest = {}, dismissButton = {
                TextButton(onClick = {
                    vm.uploadStatusFlow.value = null
                }) {
                    Text(text = "关闭")
                }
            }, confirmButton = {
                TextButton(onClick = {
                    ClipboardUtils.copyText(IMPORT_BASE_URL + uploadStatusVal.result.id)
                    toast("复制成功")
                    vm.uploadStatusFlow.value = null
                }) {
                    Text(text = "复制")
                }
            })
        }

        else -> {}
    }

    if (showDeleteDlg) {
        AlertDialog(onDismissRequest = { showDeleteDlg = false },
            title = { Text(text = "是否删除全部快照记录?") },
            confirmButton = {
                TextButton(
                    onClick = scope.launchAsFn(Dispatchers.IO) {
                        showDeleteDlg = false
                        snapshots.forEach { s ->
                            SnapshotExt.removeAssets(s.id)
                        }
                        DbSet.snapshotDao.deleteAll()
                    },
                ) {
                    Text(text = "是", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDlg = false
                }) {
                    Text(text = "否")
                }
            })
    }
}


