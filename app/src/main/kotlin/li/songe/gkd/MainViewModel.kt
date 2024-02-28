package li.songe.gkd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.service.updateLauncherAppId
import li.songe.gkd.util.authActionFlow
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.logZipDir
import li.songe.gkd.util.newVersionApkDir
import li.songe.gkd.util.snapshotZipDir
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateSubscription

class MainViewModel : ViewModel() {
    init {
        // 在某些机型由于未知原因创建失败
        // 在此保证每次重新打开APP都能重新检测创建
        initFolder()

        // 每次打开页面更新记录桌面 appId
        updateLauncherAppId()

        val localSubsItem = SubsItem(
            id = -2, order = -2, mtime = System.currentTimeMillis()
        )
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.query().first()
            if (!subsItems.any { s -> s.id == localSubsItem.id }) {
                updateSubscription(
                    RawSubscription(
                        id = localSubsItem.id,
                        name = "本地订阅",
                        version = 0
                    )
                )
                DbSet.subsItemDao.insert(localSubsItem)
            }
        }

        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            listOf(snapshotZipDir, newVersionApkDir, logZipDir).forEach { dir ->
                if (dir.isDirectory && dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            file.delete()
                        }
                    }
                }
            }
        }

        if (storeFlow.value.autoCheckAppUpdate) {
            viewModelScope.launch {
                try {
                    checkUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        authActionFlow.value = null
    }
}