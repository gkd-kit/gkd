package li.songe.gkd

import androidx.activity.ComponentActivity

open class CompositionActivity : ComponentActivity() {
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

//    private val startHookList = linkedSetOf<() -> Unit>();
//    override fun onStart() {
//        super.onStart()
//        startHookList.forEach { block ->
//            block()
//        }
//    }
//
//    fun onStart(block: () -> Unit): () -> Boolean {
//        startHookList.add(block)
//        return {
//            startHookList.remove(block)
//        }
//    }
//
//    private val stopHookList = linkedSetOf<() -> Unit>();
//    override fun onStop() {
//        super.onStop()
//        stopHookList.forEach { block ->
//            block()
//        }
//    }
//
//    fun onStop(block: () -> Unit): () -> Boolean {
//        stopHookList.add(block)
//        return {
//            stopHookList.remove(block)
//        }
//    }
//
//    override fun onRestart() {
//        super.onRestart()
//    }
}