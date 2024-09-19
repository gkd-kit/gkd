package li.songe.gkd.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.MainActivity
import li.songe.gkd.MainViewModel
import li.songe.gkd.util.isSafeUrl
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class InputSubsLinkOption {
    private val showFlow = MutableStateFlow(false)
    private val valueFlow = MutableStateFlow("")
    private val initValueFlow = MutableStateFlow("")
    private var continuation: Continuation<String?>? = null

    private fun resume(value: String?) {
        showFlow.value = false
        valueFlow.value = ""
        initValueFlow.value = ""
        continuation?.resume(value)
        continuation = null
    }

    private suspend fun submit(mainVm: MainViewModel) {
        val value = valueFlow.value
        if (!URLUtil.isNetworkUrl(value)) {
            toast("非法链接")
            return
        }
        val initValue = initValueFlow.value
        if (initValue.isNotEmpty() && initValue == value) {
            toast("未修改")
            resume(null)
            return
        }
        if (subsItemsFlow.value.any { it.updateUrl == value }) {
            toast("已有相同链接订阅")
            return
        }
        if (!isSafeUrl(value)) {
            mainVm.dialogFlow.waitResult(
                title = "未知来源",
                text = "你正在添加一个未验证的远程订阅\n\n这可能含有恶意的规则\n\n是否仍然确认添加?"
            )
        }
        resume(value)
    }

    private fun cancel() = resume(null)

    suspend fun getResult(initValue: String = ""): String? {
        initValueFlow.value = initValue
        valueFlow.value = initValue
        showFlow.value = true
        return suspendCoroutine {
            continuation = it
        }
    }

    @Composable
    fun ContentDialog() {
        val show by showFlow.collectAsState()
        if (show) {
            val context = LocalContext.current as MainActivity
            val value by valueFlow.collectAsState()
            val initValue by initValueFlow.collectAsState()
            AlertDialog(
                title = {
                    Text(text = if (initValue.isNotEmpty()) "修改订阅" else "添加订阅")
                },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            valueFlow.value = it.trim()
                        },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(text = "请输入订阅链接")
                        },
                        isError = value.isNotEmpty() && !URLUtil.isNetworkUrl(value),
                    )
                },
                onDismissRequest = {
                    if (valueFlow.value.isEmpty()) {
                        cancel()
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = value.isNotEmpty(),
                        onClick = throttle(fn = context.mainVm.viewModelScope.launchAsFn {
                            submit(context.mainVm)
                        }),
                    ) {
                        Text(text = "确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = "取消")
                    }
                },
            )
        }
    }
}

