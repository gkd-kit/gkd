package li.songe.gkd.priv

import android.app.AppOpsManager
import android.content.Context
import com.android.internal.app.IAppOpsService
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatAppOpsService {
    val value: IAppOpsService = IAppOpsService.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.APP_OPS_SERVICE),
        ),
    )

    companion object {
        val supportA11yOverlay by lazy { AppOpsManager::class.detectHiddenField("OP_CREATE_ACCESSIBILITY_OVERLAY") }
    }
}
