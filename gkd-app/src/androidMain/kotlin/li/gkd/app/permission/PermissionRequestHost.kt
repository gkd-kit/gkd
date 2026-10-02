package li.gkd.app.permission

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.hjq.permissions.OnPermissionDescription
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PermissionRequestHost(
    private val activity: ComponentActivity,
) : DefaultLifecycleObserver {
    private var commands: PermissionHostCommands? = null
    private var bindingJob: Job? = null
    private var onDetachHost: (() -> Unit)? = null
    private var onDispose: (() -> Unit)? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    fun bind(
        commands: PermissionHostCommands,
        coordinator: PermissionRequestCoordinator,
        onDetachHost: () -> Unit,
        onDispose: () -> Unit,
    ) {
        if (this.commands === commands) return
        bindingJob?.cancel()
        this.commands = commands
        this.onDetachHost = onDetachHost
        this.onDispose = onDispose
        bindingJob = activity.lifecycleScope.launch {
            commands.collectCommands { command ->
                execute(commands, coordinator, command)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        bindingJob?.cancel()
        bindingJob = null
        if (activity.isChangingConfigurations) {
            onDetachHost?.invoke()
        } else {
            onDispose?.invoke()
        }
        commands = null
        onDetachHost = null
        onDispose = null
    }

    private fun execute(
        commands: PermissionHostCommands,
        coordinator: PermissionRequestCoordinator,
        command: PermissionHostCommands.Command,
    ) {
        when (command) {
            is PermissionHostCommands.Command.RequestPermission -> {
                requestPermission(commands, coordinator, command)
            }

            is PermissionHostCommands.Command.OpenPermissionSettings -> {
                openPermissionSettings(commands, command)
            }
        }
    }

    private fun requestPermission(
        commands: PermissionHostCommands,
        coordinator: PermissionRequestCoordinator,
        command: PermissionHostCommands.Command.RequestPermission,
    ) {
        if (XXPermissions.isGrantedPermission(activity, command.permission)) {
            commands.complete(command.id)
            return
        }
        val description = GkdPermissionDescription(
            prompt = command.prompt,
            coordinator = coordinator,
        )
        runCatching {
            XXPermissions.with(activity)
                .unchecked()
                .permission(command.permission)
                .description(description)
                .request { _, _ ->
                    commands.complete(command.id)
                }
        }.onFailure { error ->
            commands.fail(command.id, error)
        }
    }

    private fun openPermissionSettings(
        commands: PermissionHostCommands,
        command: PermissionHostCommands.Command.OpenPermissionSettings,
    ) {
        runCatching {
            XXPermissions.startPermissionActivity(activity, command.permission) { _, _ ->
                commands.complete(command.id)
            }
        }.onFailure { error ->
            commands.fail(command.id, error)
        }
    }
}

private class GkdPermissionDescription(
    private val prompt: PermissionPrompt,
    private val coordinator: PermissionRequestCoordinator,
) : OnPermissionDescription {
    private var promptSession: AutoCloseable? = null

    override fun askWhetherRequestPermission(
        activity: Activity,
        requestList: List<IPermission>,
        continueRequestRunnable: Runnable,
        breakRequestRunnable: Runnable,
    ) {
        continueRequestRunnable.run()
    }

    override fun onRequestPermissionStart(
        activity: Activity,
        requestList: List<IPermission>,
    ) {
        promptSession = coordinator.beginPrompt(prompt)
    }

    override fun onRequestPermissionEnd(
        activity: Activity,
        requestList: List<IPermission>,
    ) {
        promptSession?.close()
        promptSession = null
    }
}
