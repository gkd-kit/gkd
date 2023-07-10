package li.songe.gkd.shizuku


import android.app.IActivityTaskManager
import android.content.pm.IPackageManager
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

val activityTaskManager: IActivityTaskManager by lazy {
    SystemServiceHelper.getSystemService("activity_task")
        .let(::ShizukuBinderWrapper)
        .let(IActivityTaskManager.Stub::asInterface)
}

val iPackageManager: IPackageManager by lazy {
    SystemServiceHelper.getSystemService("package")
        .let(::ShizukuBinderWrapper)
        .let(IPackageManager.Stub::asInterface)
}