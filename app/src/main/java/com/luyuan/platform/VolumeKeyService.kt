package com.luyuan.platform

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.luyuan.MainActivity

/**
 * 实验性：双击「音量下键」直接开始录音。
 * 原理：无障碍服务全局监听按键（安卓唯一合法的全局按键通道，无需 adb）。
 * 注意：不消费按键——音量照常变化，只是多识别一次双击节奏。
 * 后台拉起界面依赖「显示在其他应用上层」权限（Settings.canDrawOverlays）。
 */
class VolumeKeyService : AccessibilityService() {

    private var lastDownAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            val now = SystemClock.uptimeMillis()
            if (now - lastDownAt < DOUBLE_PRESS_MS) {
                lastDownAt = 0L
                launchRecord()
            } else {
                lastDownAt = now
            }
        }
        return false // 不消费：音量照常，只做双击识别
    }

    private fun launchRecord() {
        if (!Settings.canDrawOverlays(this)) return
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("auto", "record")
        }
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        private const val DOUBLE_PRESS_MS = 600L
        var running = false
            private set
    }
}
