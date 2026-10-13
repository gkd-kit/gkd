package li.gkd.app

import android.content.pm.ApplicationInfo
import li.gkd.app.data.appinfo.PackageAppCatalog.selfAppInfo

actual val META: AppMeta by lazy {
    AppMeta(
        channel = app.getMetaString("channel"),
        buildKey = app.getMetaString("buildKey"),
        commitId = app.getMetaString("commitId"),
        commitTime = app.getMetaString("commitTime").toLong(),
        tagName = app.getMetaString("tagName").takeIf { it.isNotEmpty() },
        debuggable = app.applicationInfoWithMetadata.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        versionCode = selfAppInfo.versionCode,
        versionName = selfAppInfo.versionName!!,
        appId = app.packageName!!,
        appName = app.applicationInfoWithMetadata.loadLabel(app.packageManager).toString(),
    )
}
