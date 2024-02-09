package li.songe.gkd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.authActionFlow
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.logZipDir
import li.songe.gkd.util.newVersionApkDir
import li.songe.gkd.util.snapshotZipDir
import li.songe.gkd.util.storeFlow

class MainViewModel : ViewModel() {
    init {
        appScope.launchTry(Dispatchers.IO) {
            val localSubsItem = SubsItem(
                id = -2, order = -2, mtime = System.currentTimeMillis()
            )
            if (!DbSet.subsItemDao.query().first().any { s -> s.id == localSubsItem.id }) {
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

        viewModelScope.launchTry(Dispatchers.IO) {
            // 在某些机型由于未知原因创建失败
            // 在此保证每次重新打开APP都能重新检测创建
            initFolder()
        }

        if (storeFlow.value.autoCheckAppUpdate) {
            appScope.launch {
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