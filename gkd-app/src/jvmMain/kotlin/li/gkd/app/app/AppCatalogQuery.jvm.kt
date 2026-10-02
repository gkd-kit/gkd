package li.gkd.app.app

import li.gkd.app.DesktopRuntime

actual suspend fun openAppCatalog(): AppCatalogQuery = DesktopRuntime.requireCurrent().appCatalog.open()
