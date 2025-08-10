package li.songe.gkd.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.ShortUrlSet
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

    private fun submit() {
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
            val mainVm = LocalMainViewModel.current
            val value by valueFlow.collectAsState()
            val initValue by initValueFlow.collectAsState()
            AlertDialog(
                properties = DialogProperties(dismissOnClickOutside = false),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = if (initValue.isNotEmpty()) "修改订阅" else "添加订阅")
                        IconButton(onClick = throttle {
                            cancel()
                            mainVm.navigatePage(WebViewPageDestination(initUrl = ShortUrlSet.URL5))
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = null,
                            )
                        }
                    }
                },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            valueFlow.value = it.trim()
                        },
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        placeholder = {
                            Text(text = "请输入订阅链接")
                        },
                        isError = value.isNotEmpty() && !URLUtil.isNetworkUrl(value),
                    )
                },
                onDismissRequest = {
                    cancel()
                },
                confirmButton = {
                    TextButton(
                        enabled = value.isNotEmpty(),
                        onClick = throttle(fn = {
                            submit()
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
