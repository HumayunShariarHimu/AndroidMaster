package com.androidmaster.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { if (instance === this) instance = null }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, callback: (Boolean) -> Unit) {
        val width = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val height = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val path = Path().apply {
            moveTo(x1.coerceIn(0f,1f)*width, y1.coerceIn(0f,1f)*height)
            lineTo(x2.coerceIn(0f,1f)*width, y2.coerceIn(0f,1f)*height)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(100L,1500L)))
            .build()
        dispatchGesture(gesture, object:GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = callback(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = callback(false)
        }, null)
    }

    fun tap(x: Float, y: Float, callback: (Boolean) -> Unit) {
        val width = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val height = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val px = x.coerceIn(0f, 1f) * width
        val py = y.coerceIn(0f, 1f) * height
        val path = Path().apply { moveTo(px, py) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80L))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = callback(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = callback(false)
        }, null)
    }

    fun launchPackage(packageName:String):String? {
        val launch=packageManager.getLaunchIntentForPackage(packageName)
            ?: return "No launch activity available for this package"
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { startActivity(launch); null } catch (e:Exception) { e.message ?: "Android blocked the launch request" }
    }

    fun globalAction(action: String): Boolean = when(action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }

    companion object {
        @Volatile var instance: RemoteAccessibilityService? = null
            private set
    }
}
