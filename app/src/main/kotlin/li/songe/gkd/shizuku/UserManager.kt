package li.songe.gkd.shizuku

import android.content.Context
import android.os.IUserManager
import li.songe.gkd.data.UserInfo
import li.songe.gkd.util.checkExistClass
import kotlin.reflect.typeOf

private var getUsersFcType: Int? = null
private fun IUserManager.compatGetUsers(
    excludePartial: Boolean,
    excludeDying: Boolean,
    excludePreCreated: Boolean,
): List<UserInfo> {
    getUsersFcType = getUsersFcType ?: findCompatMethod(
        "getUsers",
        listOf(
            1 to listOf(typeOf<Boolean>()),
            3 to listOf(typeOf<Boolean>(), typeOf<Boolean>(), typeOf<Boolean>()),
        )
    )
    return when (getUsersFcType) {
        1 -> getUsers(excludeDying)
        3 -> getUsers(excludePartial, excludeDying, excludePreCreated)
        else -> emptyList()
    }.map { UserInfo(id = it.id, name = it.name) }
}

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
    ): List<UserInfo> {
        return safeInvokeMethod {
            value.compatGetUsers(excludePartial, excludeDying, excludePreCreated)
        } ?: emptyList()
    }
}
