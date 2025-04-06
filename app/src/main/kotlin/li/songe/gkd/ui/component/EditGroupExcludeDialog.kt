package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Composable
fun EditGroupExcludeDialog(
    subs: RawSubscription,
    groupKey: Int,
    appId: String? = null,
    subsConfig: SubsConfig?,
    onDismissRequest: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    var value by remember {
        mutableStateOf(
            ExcludeData.parse(subsConfig?.exclude).stringify(appId)
        )
    }
    val oldValue = remember { value }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = "编辑禁用") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().autoFocus(),
                placeholder = {
                    Text(
                        text = "请填入需要禁用的 activityId\n以换行或英文逗号分割",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                minLines = 8,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodySmall
            )
        },
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "取消")
            }
        },
        confirmButton = {
            TextButton(onClick = throttle {
                if (oldValue == value) {
                    toast("禁用项无变动")
                    onDismissRequest()
                } else {
                    onDismissRequest()
                    val newSubsConfig = (subsConfig ?: SubsConfig(
                        type = SubsConfig.AppGroupType,
                        subsId = subs.id,
                        appId = appId!!,
                        groupKey = groupKey,
                    )).copy(exclude = ExcludeData.parse(appId!!, value).stringify())
                    mainVm.viewModelScope.launchTry {
                        DbSet.subsConfigDao.insert(newSubsConfig)
                        toast("更新成功")
                    }
                }
            }) {
                Text(text = "更新")
            }
        },
    )
}