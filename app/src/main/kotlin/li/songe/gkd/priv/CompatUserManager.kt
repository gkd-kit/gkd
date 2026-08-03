package li.songe.gkd.priv

import android.content.Context
import android.os.IUserManager
import li.songe.gkd.data.UserInfo
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatUserManager {
    val value: IUserManager = IUserManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.USER_SERVICE),
        ),
    )

    companion object {
        private const val GET_USERS_ONE_ARGUMENT = 1
        private const val GET_USERS_THREE_ARGUMENTS = 2

        // https://diff.songe.li/i/IUserManager.getUsers
        private val getUsersType by lazy {
            IUserManager::class.detectHiddenMethod(
                "getUsers",
                GET_USERS_ONE_ARGUMENT to listOf(Boolean::class),
                GET_USERS_THREE_ARGUMENTS to listOf(
                    Boolean::class,
                    Boolean::class,
                    Boolean::class,
                ),
            )
        }
    }

    fun getUsers(
        excludePartial: Boolean = true,
        excludeDying: Boolean = true,
        excludePreCreated: Boolean = true
    ): List<UserInfo> {
        return when (getUsersType) {
            GET_USERS_ONE_ARGUMENT -> value.getUsers(excludeDying)
            GET_USERS_THREE_ARGUMENTS -> value.getUsers(
                excludePartial,
                excludeDying,
                excludePreCreated,
            )

            else -> value.getUsers(excludeDying)
        }.map { UserInfo(id = it.id, name = it.name) }
    }
}
