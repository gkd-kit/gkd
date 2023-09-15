package li.songe.gkd.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import li.songe.gkd.appScope
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.launchTry
import javax.inject.Inject

@HiltViewModel
class HomePageVm @Inject constructor() : ViewModel() {
    val tabFlow = MutableStateFlow(controlNav)
    val intentFlow = MutableStateFlow<Intent?>(null)

    init {
        appScope.launchTry(Dispatchers.IO) {
            val localSubsItem = SubsItem(
                id = -2, order = -2, mtime = System.currentTimeMillis()
            )
            if (!DbSet.subsItemDao.query().first().any { s -> s.id == localSubsItem.id }) {
                DbSet.subsItemDao.insert(localSubsItem)
            }
            if (!localSubsItem.subsFile.exists()) {
                localSubsItem.subsFile.writeText(
                    Singleton.json.encodeToString(
                        SubscriptionRaw(
                            id = localSubsItem.id,
                            name = "本地订阅",
                            version = 0,
                            author = "gkd",
                        )
                    )
                )
            }

        }
    }
}