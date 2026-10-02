package li.gkd.app.app

import li.gkd.app.data.appinfo.PackageAppCatalog

actual suspend fun openAppCatalog(): AppCatalogQuery = PackageAppCatalog.open()
