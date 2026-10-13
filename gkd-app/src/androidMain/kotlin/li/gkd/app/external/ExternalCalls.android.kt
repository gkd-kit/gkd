package li.gkd.app.external

import li.gkd.app.service.ExposeService
import li.gkd.app.util.componentName

actual fun externalCallComponent(): String = ExposeService::class.componentName.flattenToString()
