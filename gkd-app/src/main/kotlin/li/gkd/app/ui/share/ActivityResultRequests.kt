package li.gkd.app.ui.share

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ActivityResultRequests {
    private val activityRequest = SuspendedActivityResultRequest<Intent, ActivityResult>()
    private val documentRequest = SuspendedActivityResultRequest<Array<String>, Uri?>()
    private val imageRequest = SuspendedActivityResultRequest<PickVisualMediaRequest, Uri?>()
    private val imageRequestInput =
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    suspend fun startActivity(intent: Intent): ActivityResult =
        activityRequest.launchForResult(intent)

    suspend fun openDocument(vararg mimeTypes: String): Uri? =
        documentRequest.launchForResult(arrayOf(*mimeTypes))

    suspend fun pickImage(): Uri? = imageRequest.launchForResult(imageRequestInput)

    private fun cancelPendingRequests() {
        activityRequest.dispose()
        documentRequest.dispose()
        imageRequest.dispose()
    }

    class Host(
        private val activity: ComponentActivity,
    ) : DefaultLifecycleObserver {
        private val activityLauncher = registerLauncher(
            ActivityResultContracts.StartActivityForResult(),
        ) { requests, result ->
            requests.activityRequest.resume(result)
        }
        private val documentLauncher = registerLauncher(
            ActivityResultContracts.OpenDocument(),
        ) { requests, result ->
            requests.documentRequest.resume(result)
        }
        private val imageLauncher = registerLauncher(
            ActivityResultContracts.PickVisualMedia(),
        ) { requests, result ->
            requests.imageRequest.resume(result)
        }

        private var requests: ActivityResultRequests? = null
        private val bindingJobs = mutableListOf<Job>()

        init {
            activity.lifecycle.addObserver(this)
        }

        fun bind(requests: ActivityResultRequests) {
            if (this.requests === requests) return
            bindingJobs.forEach { it.cancel() }
            bindingJobs.clear()
            this.requests = requests
            bindingJobs += bindRequest(requests.activityRequest) { input ->
                activityLauncher.launch(input)
            }
            bindingJobs += bindRequest(requests.documentRequest) { input ->
                documentLauncher.launch(input)
            }
            bindingJobs += bindRequest(requests.imageRequest) { input ->
                imageLauncher.launch(input)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            bindingJobs.forEach { it.cancel() }
            bindingJobs.clear()
            if (!activity.isChangingConfigurations) {
                requests?.cancelPendingRequests()
            }
            requests = null
            owner.lifecycle.removeObserver(this)
        }

        private fun <I, O> registerLauncher(
            contract: ActivityResultContract<I, O>,
            onResult: (ActivityResultRequests, O) -> Unit,
        ) = activity.registerForActivityResult(contract) { result ->
            requests?.let { onResult(it, result) }
        }

        private fun <I, O> bindRequest(
            request: SuspendedActivityResultRequest<I, O>,
            launch: (I) -> Unit,
        ) = activity.lifecycleScope.launch {
            request.pendingFlow.collect {
                request.takeInputToLaunch()?.let { input ->
                    runCatching { launch(input) }
                        .onFailure(request::fail)
                }
            }
        }
    }

    private class SuspendedActivityResultRequest<I, O> {
        private data class PendingRequest<I>(
            val input: I,
            val launched: Boolean,
        )

        private val requestMutex = Mutex()
        private val mutexOwner = Any()
        private val stateLock = Any()
        private var continuation: CancellableContinuation<O>? = null
        private var ownsMutex = false

        val pendingFlow: StateFlow<PendingRequest<I>?>
            field = MutableStateFlow(null)

        suspend fun launchForResult(input: I): O = withContext(Dispatchers.Main.immediate) {
            requestMutex.lock(mutexOwner)
            ownsMutex = true
            suspendCancellableCoroutine { continuation ->
                if (!continuation.isActive) {
                    releaseMutex()
                    return@suspendCancellableCoroutine
                }
                synchronized(stateLock) {
                    this@SuspendedActivityResultRequest.continuation = continuation
                    pendingFlow.value = PendingRequest(input = input, launched = false)
                }
                continuation.invokeOnCancellation {
                    synchronized(stateLock) {
                        if (this@SuspendedActivityResultRequest.continuation === continuation) {
                            this@SuspendedActivityResultRequest.continuation = null
                            if (pendingFlow.value?.launched == false) {
                                pendingFlow.value = null
                                releaseMutex()
                            }
                        }
                    }
                }
            }
        }

        fun takeInputToLaunch(): I? = synchronized(stateLock) {
            val pending = pendingFlow.value ?: return@synchronized null
            if (pending.launched) return@synchronized null
            pendingFlow.value = pending.copy(launched = true)
            pending.input
        }

        fun resume(result: O) {
            val currentContinuation = synchronized(stateLock) {
                val current = continuation
                continuation = null
                pendingFlow.value = null
                releaseMutex()
                current
            }
            if (currentContinuation?.isActive == true) {
                currentContinuation.resume(result)
            }
        }

        fun fail(error: Throwable) {
            val currentContinuation = synchronized(stateLock) {
                val current = continuation
                continuation = null
                pendingFlow.value = null
                releaseMutex()
                current
            }
            if (currentContinuation?.isActive == true) {
                currentContinuation.resumeWithException(error)
            }
        }

        fun dispose() {
            val currentContinuation = synchronized(stateLock) {
                val current = continuation
                continuation = null
                pendingFlow.value = null
                releaseMutex()
                current
            }
            currentContinuation?.cancel()
        }

        private fun releaseMutex() {
            if (!ownsMutex) return
            ownsMutex = false
            requestMutex.unlock(mutexOwner)
        }
    }
}
