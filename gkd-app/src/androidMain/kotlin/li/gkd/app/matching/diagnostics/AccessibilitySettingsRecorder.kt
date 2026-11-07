package li.gkd.app.matching.diagnostics

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.ServiceManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.app
import li.gkd.app.util.dump

/** One diagnostic session owns the observer and the serial background collector. */
class AccessibilitySettingsRecorder {
    private val thread = HandlerThread("AccessibilitySettingsObserver").apply { start() }
    private val requests = Channel<Pair<Long, String>>(Channel.UNLIMITED)
    private val observer =
        object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                requests.trySend(System.currentTimeMillis() to (uri?.lastPathSegment ?: "changed"))
            }
        }

    private fun start() {
        for (name in
            listOf(
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            )) {
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(name),
                false,
                observer,
            )
        }
        requests.trySend(System.currentTimeMillis() to "baseline")
    }

    suspend fun collect(
        untilStopped: suspend () -> Unit,
        onCollected: (List<AccessibilitySettingRecord>, Long) -> Unit,
    ) = coroutineScope {
        val records = ArrayDeque<AccessibilitySettingRecord>()
        var dropped = 0L
        val worker =
            launch(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                var binder: IBinder? = null
                val changes = AccessibilitySettingChanges()
                for ((notifiedAt, trigger) in requests) {
                    val readAt = System.currentTimeMillis()
                    val values =
                        try {
                            val service =
                                binder
                                    ?: checkNotNull(ServiceManager.getService("settings")).also {
                                        binder = it
                                    }
                            service.dump { reader ->
                                parseAccessibilitySettingsDump(
                                    reader.lineSequence(),
                                    notifiedAt,
                                    readAt,
                                    trigger,
                                )
                            }
                        } catch (error: Exception) {
                            listOf(
                                AccessibilitySettingRecord(
                                    notifiedAt = notifiedAt,
                                    readAt = readAt,
                                    trigger = trigger,
                                    error = error.toString(),
                                ),
                            )
                        }
                    for (record in values) {
                        val change = changes.accept(record) ?: continue
                        if (records.size == 2_000) {
                            records.removeFirst()
                            dropped++
                        }
                        records.addLast(change)
                    }
                }
            }
        try {
            start()
            untilStopped()
        } finally {
            app.contentResolver.unregisterContentObserver(observer)
            thread.quitSafely()
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                thread.join()
                requests.close()
                worker.join()
                onCollected(records.toList(), dropped)
            }
        }
    }
}
