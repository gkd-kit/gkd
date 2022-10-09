package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

abstract class CompositionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val destroyHookList = linkedSetOf<() -> Unit>();
    override fun onDestroy() {
        super.onDestroy()
        destroyHookList.forEach { block ->
            block()
        }
    }

    fun onDestroy(block: () -> Unit): () -> Boolean {
        destroyHookList.add(block)
        return {
            destroyHookList.remove(block)
        }
    }

    private val startCommandHookList =
        linkedSetOf<(intent: Intent?, flags: Int, startId: Int) -> Unit>();

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    fun onStartCommand(block: (intent: Intent?, flags: Int, startId: Int) -> Unit): () -> Boolean {
        startCommandHookList.add(block)
        return {
            startCommandHookList.remove(block)
        }
    }

}