package li.songe.gkd.priv.shizuku

import android.content.Context
import android.content.pm.PackageManager
import priv.kit.core.PrivilegeStartupLogListener
import priv.kit.ui.PrivilegeUiExternalStartSnapshot
import priv.kit.ui.PrivilegeUiStreamingExternalStartProvider
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal object GkdShizukuExternalStartProvider :
    PrivilegeUiStreamingExternalStartProvider {
    override val id: String = "shizuku"
    override val label: CharSequence = "Shizuku"

    override suspend fun snapshot(context: Context): PrivilegeUiExternalStartSnapshot =
        runCatching {
            shizukuSnapshot()
        }.getOrElse { throwable ->
            PrivilegeUiExternalStartSnapshot(
                message = throwable.message ?: throwable.javaClass.name,
                exceptionText = throwable.stackTraceToString(),
            )
        }

    override suspend fun requestAuthorization(
        context: Context,
    ): PrivilegeUiExternalStartSnapshot {
        val current = snapshot(context)
        if (
            !current.available ||
            current.canStart ||
            Shizuku.shouldShowRequestPermissionRationale()
        ) {
            return current
        }

        return suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            fun finish(result: PrivilegeUiExternalStartSnapshot) {
                if (!completed.compareAndSet(false, true)) return
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) continuation.resume(result)
            }

            listener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                    finish(shizukuSnapshot())
                }
            }
            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    finish(shizukuSnapshot())
                } else {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } catch (throwable: Throwable) {
                finish(
                    PrivilegeUiExternalStartSnapshot(
                        message = throwable.message ?: throwable.javaClass.name,
                        exceptionText = throwable.stackTraceToString(),
                    ),
                )
            }
        }
    }

    override suspend fun start(
        context: Context,
        commandLine: String,
    ) {
        PrivilegeShizukuExternalStarter(context).use { starter ->
            starter.start(commandLine)
        }
    }

    override suspend fun start(
        context: Context,
        commandLine: String,
        startupLogListener: PrivilegeStartupLogListener,
    ) {
        PrivilegeShizukuExternalStarter(context).use { starter ->
            starter.start(commandLine, startupLogListener)
        }
    }

    private fun shizukuSnapshot(): PrivilegeUiExternalStartSnapshot {
        if (!Shizuku.pingBinder()) {
            return PrivilegeUiExternalStartSnapshot(
                message = "Shizuku is not running",
            )
        }
        if (Shizuku.isPreV11()) {
            return PrivilegeUiExternalStartSnapshot(
                message = "Shizuku before v11 is unsupported",
            )
        }

        val version = Shizuku.getVersion()
        val uid = Shizuku.getUid().takeIf { it >= 0 }
        if (version < PrivilegeShizukuExternalStarter.SHIZUKU_USER_SERVICE_MIN_VERSION) {
            return PrivilegeUiExternalStartSnapshot(
                available = false,
                uid = uid,
                version = version,
                message = "This Shizuku version does not support UserService",
            )
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return PrivilegeUiExternalStartSnapshot(
                available = true,
                authorized = true,
                uid = uid,
                version = version,
                message = "Shizuku is ready",
            )
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            return PrivilegeUiExternalStartSnapshot(
                available = true,
                uid = uid,
                version = version,
                message = "Shizuku authorization was denied",
            )
        }

        return PrivilegeUiExternalStartSnapshot(
            available = true,
            uid = uid,
            version = version,
            message = "Shizuku authorization is required",
        )
    }

    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 42
}
