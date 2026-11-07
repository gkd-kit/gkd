package li.gkd.app.notif

import android.app.Service
import kotlin.reflect.KClass
import li.gkd.app.META
import li.gkd.app.resources.*
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.DebugService
import li.gkd.app.service.EventService
import li.gkd.app.service.LifecycleHookService
import li.gkd.app.service.MatchDiagnosticService
import li.gkd.app.service.ScreenshotService
import li.gkd.app.service.TrackService
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import li.gkd.app.ui.navigation.AdvancedSection
import li.gkd.app.util.Constants
import org.jetbrains.compose.resources.StringResource

enum class ForegroundNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel = AppNotificationChannel.Service,
) {
    Status(id = 100),
    Screenshot(id = 101),
    Button(id = 102),
    Debug(id = 103),
    Expose(id = 104),
    Activity(id = 106),
    Event(id = 107),
    Track(id = 108),
    MatchDiagnostic(id = 109),
}

enum class PostedNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel,
) {
    SnapshotSaved(id = 105, channel = AppNotificationChannel.Snapshot),
    MatchDiagnosticSaved(id = 110, channel = AppNotificationChannel.Service),
}

sealed interface AppNotificationSpec {
    val id: Int
    val channel: AppNotificationChannel
    val title: String
    val text: String?
    val uri: String?
    val ongoing: Boolean
    val autoCancel: Boolean
    val stopService: KClass<out Service>?
}

data class ForegroundNotification(
    val key: ForegroundNotificationKey,
    private val titleValue: String = "",
    private val textValue: String? = null,
    private val titleResource: StringResource? = null,
    private val textResource: StringResource? = null,
    override val uri: String? = null,
    override val stopService: KClass<out Service>? = null,
) : AppNotificationSpec {
    override val title: String
        get() = titleResource?.getSync() ?: titleValue

    override val text: String?
        get() = textResource?.getSync() ?: textValue

    override val id: Int
        get() = key.id

    override val channel: AppNotificationChannel
        get() = key.channel

    override val ongoing = true
    override val autoCancel = false

    context(service: LifecycleHookService)
    fun startForeground() = service.showForegroundNotification(this)
}

data class PostedNotification(
    val key: PostedNotificationKey,
    override val title: String,
    override val text: String? = null,
    override val uri: String? = null,
    override val ongoing: Boolean = false,
    override val autoCancel: Boolean = true,
) : AppNotificationSpec {
    override val id: Int
        get() = key.id

    override val channel: AppNotificationChannel
        get() = key.channel

    override val stopService: KClass<out Service>? = null

    fun post() {
        NotificationDispatcher.post(this)
    }
}

object NotificationCatalog {
    fun matchDiagnostic(saving: Boolean = false) =
        ForegroundNotification(
            key = ForegroundNotificationKey.MatchDiagnostic,
            titleResource =
                if (saving) Res.string.match_debug_saving else Res.string.match_debug_capturing,
            textResource = if (saving) null else Res.string.match_debug_notification_help,
            uri = "gkd://page/matching-diagnostics",
            stopService = if (saving) null else MatchDiagnosticService::class,
        )

    fun matchDiagnosticSaved() =
        PostedNotification(
            key = PostedNotificationKey.MatchDiagnosticSaved,
            title = Res.string.match_debug_saved.getSync(),
            text = Res.string.match_debug_saved_notification_help.getSync(),
            uri = "gkd://page/matching-diagnostics",
        )

    fun status(
        title: String = META.appName,
        text: String? = Res.string.a11y_running.getSync(),
        uri: String? = null,
    ) =
        ForegroundNotification(
            key = ForegroundNotificationKey.Status,
            titleValue = title,
            textValue = text,
            uri = uri,
        )

    fun screenshot() =
        ForegroundNotification(
            key = ForegroundNotificationKey.Screenshot,
            titleResource = Res.string.screenshot_capture_enabled,
            textResource = Res.string.screenshot_capture_notification_description,
            uri = AdvancedSection.SnapshotSettings.uri,
            stopService = ScreenshotService::class,
        )

    fun button() =
        ForegroundNotification(
            key = ForegroundNotificationKey.Button,
            titleResource = Res.string.snapshot_button_enabled,
            textResource = Res.string.snapshot_button_notification_description,
            uri = AdvancedSection.SnapshotButton.uri,
            stopService = ButtonService::class,
        )

    fun debug(port: Int, localNetworkIps: List<String> = emptyList()) =
        ForegroundNotification(
            key = ForegroundNotificationKey.Debug,
            titleResource = Res.string.debug_service_enabled,
            textValue =
                localNetworkIps
                    .ifEmpty { listOf(Constants.loopbackHost) }
                    .joinToString(", ") { "$it:$port" },
            uri = AdvancedSection.Http.uri,
            stopService = DebugService::class,
        )

    fun expose() =
        ForegroundNotification(
            key = ForegroundNotificationKey.Expose,
            titleResource = Res.string.external_call_processing,
            textResource = Res.string.external_call_notification_description,
        )

    fun snapshotSaved(
        appName: String,
        activityId: String?,
        screenshotStatus: SnapshotScreenshotStatus,
        savedToDownloads: Boolean,
        exportDetail: String? = null,
    ) =
        PostedNotification(
            key = PostedNotificationKey.SnapshotSaved,
            title = Res.string.snapshot_saved_app.getSync(appName),
            text =
                buildList {
                        activityId?.let(::add)
                        screenshotStatus.detailText()?.let(::add)
                        exportDetail?.let(::add)
                        if (savedToDownloads) add(Res.string.saved_to_downloads.getSync())
                    }
                    .joinToString(separator = " · ")
                    .takeIf { it.isNotEmpty() },
            uri = "gkd://page/2",
        )

    fun activity(text: String? = null) =
        ForegroundNotification(
            key = ForegroundNotificationKey.Activity,
            titleResource = Res.string.activity_info_showing,
            textValue = text,
            textResource = if (text == null) Res.string.unknown else null,
            uri = AdvancedSection.Activity.uri,
            stopService = ActivityService::class,
        )

    fun event() =
        ForegroundNotification(
            key = ForegroundNotificationKey.Event,
            titleResource = Res.string.a11y_events_recording,
            uri = AdvancedSection.Event.uri,
            stopService = EventService::class,
        )

    fun track() =
        ForegroundNotification(
            key = ForegroundNotificationKey.Track,
            titleResource = Res.string.track_overlay_enabled,
            uri = AdvancedSection.Track.uri,
            stopService = TrackService::class,
        )
}
