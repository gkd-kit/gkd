package li.songe.gkd.shizuku

import android.content.Context
import android.os.IUserManager
import li.songe.gkd.data.UserInfo
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass

class SafeUserManager(private val value: IUserManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.os.IUserManager")

        fun newBinder() = getStubService(
            Context.USER_SERVICE,
            isAvailable,
        )?.let {
            SafeUserManager(IUserManager.Stub.asInterface(it))
        }
    }

    fun getUsers(
        excludePartial: Boolean = true,
        excludeDying: Boolean = true,
        excludePreCreated: Boolean = true
    ): List<UserInfo> = safeInvokeMethod {
        if (AndroidTarget.R) {
            value.getUsers(excludePartial, excludeDying, excludePreCreated)
        } else {
            value.getUsers(excludeDying)
        }.map { UserInfo(id = it.id, name = it.name.trim()) }
    } ?: emptyList()
}
