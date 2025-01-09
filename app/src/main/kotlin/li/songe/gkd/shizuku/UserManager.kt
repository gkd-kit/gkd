package li.songe.gkd.shizuku

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.IUserManager
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


private fun IUserManager.compatGetUsers(
    excludePartial: Boolean = true,
    excludeDying: Boolean = true,
    excludePreCreated: Boolean = true
): List<li.songe.gkd.data.UserInfo> {
    return (if (Build.VERSION.SDK_INT >= 30) {
        getUsers(excludePartial, excludeDying, excludePreCreated)
    } else {
        try {
            getUsers(excludeDying)
        } catch (e: NoSuchFieldError) {
            LogUtils.d(e)
            @SuppressLint("NewApi")
            getUsers(excludePartial, excludeDying, excludePreCreated)
        }
    }).map {
        li.songe.gkd.data.UserInfo(
            id = it.id,
            name = it.name.trim(),
        )
    }
}

interface SafeUserManager {
    fun compatGetUsers(
        excludePartial: Boolean,
        excludeDying: Boolean,
        excludePreCreated: Boolean
    ): List<li.songe.gkd.data.UserInfo>

    fun compatGetUsers(): List<li.songe.gkd.data.UserInfo>
}

fun newUserManager(): SafeUserManager? {
    val service = SystemServiceHelper.getSystemService(Context.USER_SERVICE)
    if (service == null) {
        LogUtils.d("shizuku 无法获取 user")
        return null
    }
    val manager = service.let(::ShizukuBinderWrapper).let(IUserManager.Stub::asInterface)
    return object : SafeUserManager {
        override fun compatGetUsers(
            excludePartial: Boolean,
            excludeDying: Boolean,
            excludePreCreated: Boolean
        ) = manager.compatGetUsers(excludePartial, excludeDying, excludePreCreated)

        override fun compatGetUsers() = manager.compatGetUsers()
    }
}


val userManagerFlow by lazy<StateFlow<SafeUserManager?>> {
    val stateFlow = MutableStateFlow<SafeUserManager?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuWorkProfileUsedFlow.collect {
            stateFlow.value = if (it) newUserManager() else null
        }
    }
    stateFlow
}
