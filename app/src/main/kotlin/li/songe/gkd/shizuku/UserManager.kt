package li.songe.gkd.shizuku

import android.content.Context
import android.os.IUserManager
import li.songe.gkd.data.UserInfo

class SafeUserManager(private val value: IUserManager) {
    companion object {

        fun newBinder() = getShizukuService(Context.USER_SERVICE)?.let {
            SafeUserManager(IUserManager.Stub.asInterface(it))
        }

        private val getUsersType by lazy {
            IUserManager::class.java.detectHiddenMethod(
                "getUsers",
                1 to listOf(Boolean::class.java),
                2 to listOf(Boolean::class.java, Boolean::class.java, Boolean::class.java),
            )
        }
    }

    fun getUsers(
        excludePartial: Boolean = true,
        excludeDying: Boolean = true,
        excludePreCreated: Boolean = true
    ): List<UserInfo> = safeInvokeShizuku {
        when (getUsersType) {
            1 -> value.getUsers(excludeDying)
            2 -> value.getUsers(excludePartial, excludeDying, excludePreCreated)
            else -> value.getUsers(excludeDying)
        }.map { UserInfo(id = it.id, name = it.name.trim()) }
    } ?: emptyList()
}
