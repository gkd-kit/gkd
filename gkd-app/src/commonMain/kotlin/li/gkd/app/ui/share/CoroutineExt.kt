package li.gkd.app.ui.share

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import li.gkd.app.error.resolveMessage
import li.gkd.app.inspection.InspectionException
import li.gkd.app.snapshot.SnapshotCaptureBusyException
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import li.songe.codeorigin.CallSite

fun CoroutineScope.launchUi(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    @CallSite loc: String = "",
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context, start) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LogUtils.d(e, loc = loc)
        ToastUtils.show(
            e.resolveMessage(),
            forced = e is InspectionException || e is SnapshotCaptureBusyException,
            loc = "",
        )
    }
}

fun CoroutineScope.launchUiAction(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    @CallSite loc: String = "",
    block: suspend CoroutineScope.() -> Unit,
): () -> Unit = {
    launchUi(context = context, start = start, loc = loc, block = block)
}

fun <T> CoroutineScope.launchUiAction(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    @CallSite loc: String = "",
    block: suspend CoroutineScope.(T) -> Unit,
): (T) -> Unit = { value ->
    launchUi(context = context, start = start, loc = loc) { block(value) }
}
