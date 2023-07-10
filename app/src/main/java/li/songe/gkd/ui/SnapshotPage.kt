package li.songe.gkd.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.utils.launchAsFn
import li.songe.gkd.utils.Singleton

@RootNavGraph
@Destination
@Composable
fun SnapshotPage() {
    val context = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()

    var snapshots by remember {
        mutableStateOf(listOf<Snapshot>())
    }
    var selectedSnapshot by remember {
        mutableStateOf<Snapshot?>(null)
    }
    LaunchedEffect(Unit) {
        DbSet.snapshotDao.query().flowOn(Dispatchers.IO).collect {
            snapshots = it.reversed()
        }
    }
    LazyColumn(
        modifier = Modifier.padding(10.dp, 0.dp, 10.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(text = "存在 ${snapshots.size} 条快照记录")
        }
        items(snapshots.size) { i ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.Black))
                    .clickable {
                        selectedSnapshot = snapshots[i]
                    }
            ) {
                Row {
                    Text(
                        text = Singleton.simpleDateFormat.format(snapshots[i].id),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = snapshots[i].appName ?: "")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = snapshots[i].appId ?: "")
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = snapshots[i].activityId ?: "")
            }
        }
        item {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
    selectedSnapshot?.let { snapshot ->
        Dialog(
            onDismissRequest = { selectedSnapshot = null }
        ) {
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
//                                router.navigate(
//                                    ImagePreviewPage,
//                                    SnapshotExt.getScreenshotPath(snapshot.id)
//                                )
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                    Text(
                        text = "分享", modifier = Modifier
                            .clickable(onClick = scope.launchAsFn {
                                val zipFile = SnapshotExt.getSnapshotZipFile(snapshot.id)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    zipFile
                                )
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/zip"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享zip文件"))
                            })
                            .then(modifier)
                    )
                    Text(
                        text = "删除", modifier = Modifier
                            .clickable(onClick = scope.launchAsFn {
                                DbSet.snapshotDao.delete(snapshot)
                                withContext(IO) {
                                    SnapshotExt.remove(snapshot.id)
                                }
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                }
            }
        }
    }
}


