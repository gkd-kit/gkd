package li.songe.gkd.shizuku

import android.os.IUserManager
import li.songe.gkd.data.UserInfo
import li.songe.gkd.util.toast
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.typeOf

private var getUsersFcType: Int? = null
private fun IUserManager.compatGetUsers(
    excludePartial: Boolean,
    excludeDying: Boolean,
    excludePreCreated: Boolean,
): List<UserInfo> {
    if (getUsersFcType == null) {
        for (f in this::class.declaredMemberFunctions.filter { it.name == "getUsers" }) {
            getUsersFcType = when (f.valueParameters.map { it.type }) {
                listOf(typeOf<Boolean>()) -> 1
                listOf(typeOf<Boolean>(), typeOf<Boolean>(), typeOf<Boolean>()) -> 3
                else -> null
            }
            if (getUsersFcType != null) {
                break
            }
        }
        if (getUsersFcType == null) {
            getUsersFcType = -1
            toast("获取 IUserManager:getTasks 签名错误")
        }
    }
    return try {
        when (getUsersFcType) {
            1 -> this.getUsers(excludeDying)
            3 -> this.getUsers(excludePartial, excludeDying, excludePreCreated)
            else -> emptyList()
        }
    } catch (_: Throwable) {
        emptyList()
    }.map {
        UserInfo(
            id = it.id,
            name = it.name,
        )
    }
}

class SafeUserManager(private val value: IUserManager) {
    fun compatGetUsers(
        excludePartial: Boolean = true,
        excludeDying: Boolean = true,
        excludePreCreated: Boolean = true
    ): List<UserInfo> = value.compatGetUsers(excludePartial, excludeDying, excludePreCreated)
}
