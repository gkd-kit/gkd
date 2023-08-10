package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.Singleton
import javax.inject.Inject


@HiltViewModel
class SubsManageVm @Inject constructor() : ViewModel() {
    val subsItemsFlow = DbSet.subsItemDao.query().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun addSubsFromUrl(url: String) {
        if (!URLUtil.isNetworkUrl(url)) {
            ToastUtils.showShort("非法链接")
            return
        }
        val subItems = subsItemsFlow.first()
        if (subItems.any { it.updateUrl == url }) {
            ToastUtils.showShort("订阅链接已存在")
            return
        }
        val text = try {
            Singleton.client.get(url).bodyAsText()
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showShort("下载订阅文件失败")
            return
        }
        val subscriptionRaw = try {
            SubscriptionRaw.parse5(text)
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showShort("解析订阅文件失败")
            return
        }
        val newItem = SubsItem(
            updateUrl = subscriptionRaw.updateUrl ?: url,
            name = subscriptionRaw.name,
            version = subscriptionRaw.version,
            order = subItems.size + 1
        )
        withContext(Dispatchers.IO) {
            newItem.subsFile.writeText(text)
        }
        DbSet.subsItemDao.insert(newItem)
        ToastUtils.showShort("成功添加订阅")
    }

}