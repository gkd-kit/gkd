package li.gkd.app.priv

import android.content.Context
import android.os.IUserManager
import android.os.UserManagerHidden
import li.gkd.app.app
import li.gkd.app.model.AppUser
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatUserManager {
    private val manager = UserManagerHidden(
        app,
        IUserManager.Stub.asInterface(
            requireNotNull(
                PrivilegeBinderWrapper.fromSystemService(Context.USER_SERVICE),
            ),
        ),
    )

    fun getUsers(excludeDying: Boolean = true): List<AppUser> =
        manager.getUsers(excludeDying).map { AppUser(id = it.id, name = it.name) }
}
