package li.songe.gkd.ui

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.UriUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.destinations.ImagePreviewPageDestination
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.LocalPickContentLauncher
import li.songe.gkd.util.LocalRequestPermissionLauncher
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.navigate
import li.songe.gkd.util.recordStoreFlow
import li.songe.gkd.util.snapshotZipDir
import li.songe.gkd.util.updateStorage
import java.io.File

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SnapshotPage() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current as ComponentActivity
    val navController = LocalNavController.current

    val pickContentLauncher = LocalPickContentLauncher.current
    val requestPermissionLauncher = LocalRequestPermissionLauncher.current

    val vm = hiltViewModel<SnapshotVm>()
    val snapshots by vm.snapshotsState.collectAsState()
    val uploadStatus by vm.uploadStatusFlow.collectAsState()
    val recordStore by recordStoreFlow.collectAsState()

    var selectedSnapshot by remember {
        mutableStateOf<Snapshot?>(null)
    }

    var showDeleteDlg by remember {
        mutableStateOf(false)
    }
    val scrollState = rememberLazyListState()

    Scaffold(topBar = {
        SimpleTopAppBar(onClickIcon = { navController.popBackStack() },
            title = if (snapshots.isEmpty()) "快照记录" else "快照记录-${snapshots.size}",
            actions = {
                if (snapshots.isNotEmpty()) {
                    IconButton(onClick = { showDeleteDlg = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        if (snapshots.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .padding(10.dp, 0.dp, 10.dp, 0.dp)
                    .padding(contentPadding),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(snapshots, { it.id }) { snapshot ->
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, Color.Black))
                        .clickable {
                            selectedSnapshot = snapshot
                        }) {
                        Row {
                            Text(
                                text = snapshot.id.format("yyyy-MM-dd HH:mm:ss"),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = snapshot.appName ?: "")
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = snapshot.activityId ?: "")
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(30.dp))
                Text(text = "暂无记录")
            }
        }
    })

    selectedSnapshot?.let { snapshotVal ->
        Dialog(onDismissRequest = { selectedSnapshot = null }) {
            Box(
                Modifier
                    .width(200.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                    Text(
                        text = "查看", modifier = Modifier
                            .clickable(onClick = scope.launchAsFn {
                                navController.navigate(
                                    ImagePreviewPageDestination(
                                        filePath = snapshotVal.screenshotFile.absolutePath
                                    )
                                )
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                    Text(
                        text = "分享",
                        modifier = Modifier
                            .clickable(onClick = vm.viewModelScope.launchAsFn {
                                val zipFile = SnapshotExt.getSnapshotZipFile(snapshotVal.id)
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", zipFile
                                )
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/zip"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享快照文件"))
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                    if (recordStore.snapshotIdMap.containsKey(snapshotVal.id)) {
                        Text(
                            text = "复制链接", modifier = Modifier
                                .clickable(onClick = {
                                    selectedSnapshot = null
                                    ClipboardUtils.copyText("https://gkd-kit.gitee.io/import/" + recordStore.snapshotIdMap[snapshotVal.id])
                                    ToastUtils.showShort("复制成功")
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

                    Text(
                        text = "保存截图到相册",
                        modifier = Modifier
                            .clickable(onClick = vm.viewModelScope.launchAsFn {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    val isGranted =
                                        requestPermissionLauncher.launchForResult(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    if (!isGranted) {
                                        ToastUtils.showShort("保存失败,暂无权限")
                                        return@launchAsFn
                                    }
                                }
                                ImageUtils.save2Album(
                                    ImageUtils.getBitmap(snapshotVal.screenshotFile),
                                    Bitmap.CompressFormat.PNG,
                                    true
                                )
                                ToastUtils.showShort("保存成功")
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
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
                                        File(snapshotZipDir, "${snapshotVal.id}.zip").apply {
                                            if (exists()) delete()
                                        }
                                        if (recordStore.snapshotIdMap.containsKey(snapshotVal.id)) {
                                            updateStorage(
                                                recordStoreFlow,
                                                recordStore.copy(snapshotIdMap = recordStore.snapshotIdMap
                                                    .toMutableMap()
                                                    .apply {
                                                        remove(snapshotVal.id)
                                                    })
                                            )
                                        }
                                    } else {
                                        ToastUtils.showShort("截图尺寸不一致,无法替换")
                                        return@withContext
                                    }
                                }
                                ToastUtils.showShort("替换成功")
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                    Text(
                        text = "删除", modifier = Modifier
                            .clickable(onClick = scope.launchAsFn {
                                DbSet.snapshotDao.delete(snapshotVal)
                                withContext(Dispatchers.IO) {
                                    SnapshotExt.removeAssets(snapshotVal.id)
                                    if (recordStore.snapshotIdMap.containsKey(snapshotVal.id)) {
                                        updateStorage(
                                            recordStoreFlow,
                                            recordStore.copy(snapshotIdMap = recordStore.snapshotIdMap
                                                .toMutableMap()
                                                .apply {
                                                    remove(snapshotVal.id)
                                                })
                                        )
                                    }
                                }
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                }
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
            Dialog(onDismissRequest = { }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "上传文件中,请稍等",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    LinearProgressIndicator(progress = uploadStatusVal.progress)
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            vm.uploadJob?.cancel(CancellationException("终止上传"))
                            vm.uploadJob = null
                        }) {
                            Text(text = "终止上传", color = Color.Red)
                        }
                    }
                }
            }
        }

        is LoadStatus.Success -> {
            AlertDialog(title = { Text(text = "上传完成") }, text = {
                Text(text = "https://gkd-kit.gitee.io/import/" + uploadStatusVal.result.id)
            }, onDismissRequest = {}, dismissButton = {
                TextButton(onClick = {
                    vm.uploadStatusFlow.value = null
                }) {
                    Text(text = "关闭")
                }
            }, confirmButton = {
                TextButton(onClick = {
                    ClipboardUtils.copyText("https://gkd-kit.gitee.io/import/" + uploadStatusVal.result.id)
                    ToastUtils.showShort("复制成功")
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
                TextButton(onClick = scope.launchAsFn(Dispatchers.IO) {
                    showDeleteDlg = false
                    snapshots.forEach { s ->
                        SnapshotExt.removeAssets(s.id)
                    }
                    DbSet.snapshotDao.deleteAll()
                    updateStorage(
                        recordStoreFlow, recordStoreFlow.value.copy(snapshotIdMap = emptyMap())
                    )
                }) {
                    Text(text = "是", color = Color.Red)
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


