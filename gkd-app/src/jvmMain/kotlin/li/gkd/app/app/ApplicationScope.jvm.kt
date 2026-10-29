package li.gkd.app.app

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import li.gkd.app.logging.LogUtils

private val scope by lazy {
    CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                val thread = Thread.currentThread()
                val handler = Thread.getDefaultUncaughtExceptionHandler()
                if (handler != null) handler.uncaughtException(thread, error) else LogUtils.e(error)
            },
    )
}

actual fun applicationScope(): CoroutineScope = scope
