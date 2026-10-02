package li.gkd.app.util

import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import li.gkd.app.app

// https://github.com/gkd-kit/gkd/issues/924
private val Drawable.safeDrawable: Drawable?
    get() = if (intrinsicHeight > 0 && intrinsicWidth > 0) {
        this
    } else {
        null
    }

val PackageInfo.pkgIcon: Drawable?
    get() = applicationInfo?.loadIcon(app.packageManager)?.safeDrawable
