package li.gkd.app.util

import android.os.Handler
import android.os.Looper

object ThreadUtils {
    val isMainThread: Boolean get() = Looper.getMainLooper() == Looper.myLooper()

    fun runMainOrPost(delayMillis: Long = 0L, r: Runnable) {
        if (delayMillis == 0L && isMainThread) {
            r.run()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed(r, delayMillis)
    }
}
