package li.gkd.a11y.fixture

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import java.io.File
import org.json.JSONObject

/** Separate disposable process: only this app's node provider can stall, never GKD's UI. */
class FixtureActivity : Activity() {
    private var downs = 0
    private var ups = 0
    private var reads = 0
    private var sent = 0
    private var children = 0
    private var groups = 0
    private var blockedUntil = 0L
    private var eventSource: View? = null
    private var scenario: String? = "initial"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        command(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        command(intent)
    }

    private fun command(intent: Intent) {
        when (intent.getStringExtra("operation")) {
            "report" -> {
                save()
                return
            }

            "burst" -> {
                val count = intent.getIntExtra("events", 600).coerceIn(1, 2000)
                blockedUntil =
                    SystemClock.uptimeMillis() + intent.getIntExtra("stall", 0).coerceIn(0, 4500)
                val manager = getSystemService(AccessibilityManager::class.java)
                repeat(count) { index ->
                    val event =
                        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    event.packageName = packageName
                    // Different batch keys keep the production consumer from collapsing the burst.
                    event.className = "FixtureEvent$index"
                    event.setSource(eventSource)
                    event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    event.eventTime = SystemClock.uptimeMillis()
                    manager.sendAccessibilityEvent(event)
                    sent++
                }
                save()
                return
            }
        }
        scenario = intent.getStringExtra("scenario")
        children = intent.getIntExtra("children", 4).coerceIn(0, 513)
        groups = intent.getIntExtra("groups", 1).coerceIn(1, 33)
        val forbidden = intent.getBooleanExtra("forbidden", false)
        downs = 0
        ups = 0
        reads = 0
        sent = 0
        blockedUntil = 0L
        val root =
            object : FrameLayout(this) {
                override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                    beforeRead()
                    super.onInitializeAccessibilityNodeInfo(info)
                }
            }
        root.setBackgroundColor(Color.DKGRAY)
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        root.contentDescription = "fixture-tree"
        repeat(groups) { group ->
            val parent =
                if (groups > 1) {
                    FrameLayout(this).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        contentDescription = "group-$group"
                        root.addView(this, FrameLayout.LayoutParams(-1, -1))
                    }
                } else {
                    root
                }
            repeat(children) { child ->
                val node =
                    object : View(this) {
                        override fun onInitializeAccessibilityNodeInfo(
                            info: AccessibilityNodeInfo,
                        ) {
                            beforeRead()
                            super.onInitializeAccessibilityNodeInfo(info)
                        }
                    }
                node.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                node.contentDescription =
                    if (forbidden && group == groups - 1 && child == children - 1) {
                        "forbidden"
                    } else {
                        "safe-$group-$child"
                    }
                val params =
                    FrameLayout.LayoutParams(2, 2).apply {
                        leftMargin = 4 + (child % 24) * 4
                        topMargin = 4 + (child / 24) * 4
                    }
                parent.addView(node, params)
                eventSource = node
            }
        }
        if (eventSource == null) eventSource = root
        setContentView(root)
        save()
    }

    private fun beforeRead() {
        reads++
        val remaining = blockedUntil - SystemClock.uptimeMillis()
        if (remaining > 0) SystemClock.sleep(remaining)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downs++
            MotionEvent.ACTION_UP -> ups++
        }
        save()
        return true
    }

    private fun save() {
        val json =
            JSONObject()
                .put("scenario", scenario)
                .put("children", children)
                .put("groups", groups)
                .put("downs", downs)
                .put("ups", ups)
                .put("nodeReads", reads)
                .put("eventsSent", sent)
                .put("blockedUntil", blockedUntil)
                .put("now", SystemClock.uptimeMillis())
        val pending = File(filesDir, "state.pending")
        pending.writeText(json.toString())
        check(pending.renameTo(File(filesDir, "state.json"))) { "Cannot publish fixture state" }
    }
}
