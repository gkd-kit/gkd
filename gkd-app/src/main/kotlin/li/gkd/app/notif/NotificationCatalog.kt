package li.gkd.app.notif

import android.app.Service
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.R
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.EventService
import li.gkd.app.service.HttpService
import li.gkd.app.service.ScreenshotService
import li.gkd.app.service.TrackService
import li.gkd.app.snapshot.SnapshotScreenshotStatus
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
    override val smallIcon: Int = R.drawable.ic_status,
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
    override val smallIcon: Int = R.drawable.ic_status,
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
        text: String? = UiStrings.a11y_running,
        uri: String? = null,
    ) = ForegroundNotification(
        key = ForegroundNotificationKey.Status,
        title = title,
        text = text,
        uri = uri,
    )

    fun screenshot() = ForegroundNotification(
        key = ForegroundNotificationKey.Screenshot,
        title = UiStrings.screenshot_capture_enabled,
        text = UiStrings.screenshot_capture_notification_description,
        uri = "gkd://page/1",
        stopService = ScreenshotService::class,
    )

    fun button() = ForegroundNotification(
        key = ForegroundNotificationKey.Button,
        title = UiStrings.snapshot_button_enabled,
        text = UiStrings.snapshot_button_notification_description,
        uri = "gkd://page/1",
        stopService = ButtonService::class,
    )

    fun http(port: Int, localNetworkIps: List<String> = emptyList()) = ForegroundNotification(
        key = ForegroundNotificationKey.Http,
        title = UiStrings.http_service_enabled,
        text = localNetworkIps.ifEmpty { listOf("127.0.0.1") }
            .joinToString(", ") { "$it:$port" },
        uri = "gkd://page/1",
        stopService = HttpService::class,
    )

    fun expose() = ForegroundNotification(
        key = ForegroundNotificationKey.Expose,
        title = UiStrings.external_call_processing,
        text = UiStrings.external_call_notification_description,
    )

    fun snapshotSaved(
        appName: String,
        activityId: String?,
        screenshotStatus: SnapshotScreenshotStatus,
        savedToDownloads: Boolean,
    ) = PostedNotification(
        key = PostedNotificationKey.SnapshotSaved,
        title = UiStrings.snapshot_saved_app(appName),
        text = buildList {
            activityId?.let(::add)
            screenshotStatus.detailText()?.let(::add)
            if (savedToDownloads) add(UiStrings.saved_to_downloads)
        }.joinToString(separator = " · ").takeIf { it.isNotEmpty() },
        uri = "gkd://page/2",
    )

    fun activity(text: String? = null) = ForegroundNotification(
        key = ForegroundNotificationKey.Activity,
        title = UiStrings.activity_info_showing,
        text = text,
        uri = "gkd://page/1",
        stopService = ActivityService::class,
    )

    fun event() = ForegroundNotification(
        key = ForegroundNotificationKey.Event,
        title = UiStrings.a11y_events_recording,
        uri = "gkd://page/1",
        stopService = EventService::class,
    )

    fun track() = ForegroundNotification(
        key = ForegroundNotificationKey.Track,
        title = UiStrings.track_overlay_enabled,
        uri = "gkd://page?tab=3",
        stopService = TrackService::class,
    )
}
