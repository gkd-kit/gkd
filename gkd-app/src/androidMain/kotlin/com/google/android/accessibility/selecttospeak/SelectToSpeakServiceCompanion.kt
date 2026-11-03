package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import li.gkd.app.service.CompanionServiceController

@SuppressLint("AccessibilityPolicy")
class SelectToSpeakServiceCompanion : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        CompanionServiceController.onConnected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CompanionServiceController.onDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CompanionServiceController.onDisconnected(this)
        super.onDestroy()
    }
}
