package li.gkd.app.priv

import android.content.Context
import com.android.internal.app.IAppOpsService
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatAppOpsService {
    val value: IAppOpsService = IAppOpsService.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.APP_OPS_SERVICE),
        ),
    )
}
