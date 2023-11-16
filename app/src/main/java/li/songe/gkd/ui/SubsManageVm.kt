package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.client
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import java.io.File
import javax.inject.Inject


@HiltViewModel
class SubsManageVm @Inject constructor() : ViewModel() {

    fun addSubsFromUrl(url: String) = viewModelScope.launchTry(Dispatchers.IO) {

        if (refreshingFlow.value) return@launchTry
        if (!URLUtil.isNetworkUrl(url)) {
            ToastUtils.showShort("非法链接")
            return@launchTry
        }
        val subItems = subsItemsFlow.value
        if (subItems.any { it.updateUrl == url }) {
            ToastUtils.showShort("订阅链接已存在")
            return@launchTry
        }
        refreshingFlow.value = true
        try {
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort("下载订阅文件失败")
                return@launchTry
            }
            val newSubsRaw = try {
                SubscriptionRaw.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort("解析订阅文件失败")
                return@launchTry
            }
            if (subItems.any { it.id == newSubsRaw.id }) {
                ToastUtils.showShort("订阅已存在")
                return@launchTry
            }
            if (newSubsRaw.id < 0) {
                ToastUtils.showShort("订阅id不可为${newSubsRaw.id}\n负数id为内部使用")
                return@launchTry
            }
            val newItem = SubsItem(
                id = newSubsRaw.id,
                updateUrl = newSubsRaw.updateUrl ?: url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            withContext(Dispatchers.IO) {
                val parentPath = newItem.subsFile.parent
                if (parentPath != null) {
                    // https://bugly.qq.com/v2/crash-reporting/crashes/d0ce46b353/109028?pid=1
                    File(parentPath).apply {
                        if (!exists()) {
                            mkdirs()
                        }
                    }
                }
                newItem.subsFile.writeText(text)
            }
            DbSet.subsItemDao.insert(newItem)
            ToastUtils.showShort("成功添加订阅")
        } finally {
            refreshingFlow.value = false
        }

    }

    val refreshingFlow = MutableStateFlow(false)
    fun refreshSubs() = viewModelScope.launch(Dispatchers.IO) {
        if (refreshingFlow.value) return@launch
        refreshingFlow.value = true
        var errorNum = 0
        val oldSubItems = subsItemsFlow.value
        val newSubsItems = oldSubItems.mapNotNull { oldItem ->
            if (oldItem.updateUrl == null) return@mapNotNull null
            val oldSubsRaw = subsIdToRawFlow.value[oldItem.id]
            try {
                val newSubsRaw = SubscriptionRaw.parse(
                    client.get(oldItem.updateUrl).bodyAsText()
                )
                if (oldSubsRaw != null && newSubsRaw.version <= oldSubsRaw.version) {
                    return@mapNotNull null
                }
                val newItem = oldItem.copy(
                    updateUrl = newSubsRaw.updateUrl ?: oldItem.updateUrl,
                    mtime = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) {
                    newItem.subsFile.writeText(
                        SubscriptionRaw.stringify(
                            newSubsRaw
                        )
                    )
                }
                newItem
            } catch (e: Exception) {
                e.printStackTrace()
                errorNum++
                null
            }
        }
        if (newSubsItems.isEmpty()) {
            if (errorNum == oldSubItems.size) {
                ToastUtils.showShort("更新失败")
            } else {
                ToastUtils.showShort("暂无更新")
            }
        } else {
            DbSet.subsItemDao.update(*newSubsItems.toTypedArray())
            ToastUtils.showShort("更新 ${newSubsItems.size} 条订阅")
        }
        delay(500)
        refreshingFlow.value = false
    }

}