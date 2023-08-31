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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.navigate
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.destinations.ImagePreviewPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SnapshotPage() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current as ComponentActivity
    val navController = LocalNavController.current

    val vm = hiltViewModel<SnapshotVm>()
    val snapshots by vm.snapshotsState.collectAsState()

    var selectedSnapshot by remember {
        mutableStateOf<Snapshot?>(null)
    }

    val scrollState = rememberLazyListState()

    Scaffold(topBar = {
        SimpleTopAppBar(
            onClickIcon = { navController.popBackStack() },
            title = if (snapshots.isEmpty()) "快照记录" else "快照记录-${snapshots.size}",
        )
    }, content = { contentPadding ->
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
    })

    selectedSnapshot?.let { snapshot ->
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
                                        filePath = snapshot.screenshotFile.absolutePath
                                    )
                                )
                                selectedSnapshot = null
                            })
                            .then(modifier)
                    )
                    Text(
                        text = "分享", modifier = Modifier
                            .clickable(onClick = scope.launchAsFn {
                                val zipFile = SnapshotExt.getSnapshotZipFile(snapshot.id)
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


