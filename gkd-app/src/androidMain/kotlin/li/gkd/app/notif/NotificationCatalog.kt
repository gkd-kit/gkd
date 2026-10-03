package li.gkd.app.notif

import android.app.Service
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_events_recording
import li.gkd.app.resources.a11y_running
import li.gkd.app.resources.activity_info_showing
import li.gkd.app.resources.external_call_notification_description
import li.gkd.app.resources.external_call_processing
import li.gkd.app.resources.http_service_enabled
import li.gkd.app.resources.saved_to_downloads
import li.gkd.app.resources.screenshot_capture_enabled
import li.gkd.app.resources.screenshot_capture_notification_description
import li.gkd.app.resources.snapshot_button_enabled
import li.gkd.app.resources.snapshot_button_notification_description
import li.gkd.app.resources.snapshot_saved_app
import li.gkd.app.resources.track_overlay_enabled
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.EventService
import li.gkd.app.service.HttpService
import li.gkd.app.service.ScreenshotService
import li.gkd.app.service.TrackService
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import li.gkd.app.snapshot.detailText
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.Constants
import kotlin.reflect.KClass

enum class ForegroundNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel = AppNotificationChannel.Service,
) {
    Status(id = 100),
    Screenshot(id = 101),
    Button(id = 102),
    Http(id = 103),
    Expose(id = 104),
    Activity(id = 106),
    Event(id = 107),
    Track(id = 108),
}

enum class PostedNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel,
) {
    SnapshotSaved(id = 105, channel = AppNotificationChannel.Snapshot),
}

sealed interface AppNotificationSpec {
    val id: Int
    val channel: AppNotificationChannel
    val smallIcon: Int
    val title: String
    val text: String?
    val uri: String?
    val ongoing: Boolean
    val autoCancel: Boolean
    val stopService: KClass<out Service>?
}

data class ForegroundNotification(
    val key: ForegroundNotificationKey,
    override val title: String,
    override val text: String? = null,
    override val uri: String? = null,
    override val smallIcon: Int = app.notificationSmallIcon,
    override val stopService: KClass<out Service>? = null,
) : AppNotificationSpec {
    override val id: Int
        get() = key.id
    override val channel: AppNotificationChannel
        get() = key.channel
    override val ongoing = true
    override val autoCancel = false

    context(service: Service)
    fun startForeground() = NotificationDispatcher.startForeground(service, this)
}

data class PostedNotification(
    val key: PostedNotificationKey,
    override val title: String,
    override val text: String? = null,
    override val uri: String? = null,
    override val smallIcon: Int = app.notificationSmallIcon,
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
    fun status(
        title: String = META.appName,
        text: String? = Res.string.a11y_running.getSync(),
        uri: String? = null,
    ) = ForegroundNotification(
        key = ForegroundNotificationKey.Status,
        title = title,
        text = text,
        uri = uri,
    )

    fun screenshot() = ForegroundNotification(
        key = ForegroundNotificationKey.Screenshot,
        title = Res.string.screenshot_capture_enabled.getSync(),
        text = Res.string.screenshot_capture_notification_description.getSync(),
        uri = "gkd://page/1",
        stopService = ScreenshotService::class,
    )

    fun button() = ForegroundNotification(
        key = ForegroundNotificationKey.Button,
        title = Res.string.snapshot_button_enabled.getSync(),
        text = Res.string.snapshot_button_notification_description.getSync(),
        uri = "gkd://page/1",
        stopService = ButtonService::class,
    )

    fun http(port: Int, localNetworkIps: List<String> = emptyList()) = ForegroundNotification(
        key = ForegroundNotificationKey.Http,
        title = Res.string.http_service_enabled.getSync(),
        text = localNetworkIps.ifEmpty { listOf(Constants.loopbackHost) }
            .joinToString(", ") { "$it:$port" },
        uri = "gkd://page/1",
        stopService = HttpService::class,
    )

    fun expose() = ForegroundNotification(
        key = ForegroundNotificationKey.Expose,
        title = Res.string.external_call_processing.getSync(),
        text = Res.string.external_call_notification_description.getSync(),
    )

    fun snapshotSaved(
        appName: String,
        activityId: String?,
        screenshotStatus: SnapshotScreenshotStatus,
        savedToDownloads: Boolean,
        exportDetail: String? = null,
    ) = PostedNotification(
        key = PostedNotificationKey.SnapshotSaved,
        title = Res.string.snapshot_saved_app.getSync(appName),
        text = buildList {
            activityId?.let(::add)
            screenshotStatus.detailText()?.let(::add)
            exportDetail?.let(::add)
            if (savedToDownloads) add(Res.string.saved_to_downloads.getSync())
        }.joinToString(separator = " · ").takeIf { it.isNotEmpty() },
        uri = "gkd://page/2",
    )

    fun activity(text: String? = null) = ForegroundNotification(
        key = ForegroundNotificationKey.Activity,
        title = Res.string.activity_info_showing.getSync(),
        text = text,
        uri = "gkd://page/1",
        stopService = ActivityService::class,
    )

    fun event() = ForegroundNotification(
        key = ForegroundNotificationKey.Event,
        title = Res.string.a11y_events_recording.getSync(),
        uri = "gkd://page/1",
        stopService = EventService::class,
    )

    fun track() = ForegroundNotification(
        key = ForegroundNotificationKey.Track,
        title = Res.string.track_overlay_enabled.getSync(),
        uri = "gkd://page?tab=3",
        stopService = TrackService::class,
    )
}
