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
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        instance = null
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

        @Volatile
        private var instance: VolumeKeyService? = null

        /**
         * 「双麦克风」之二：对屏幕底部中央做一次长按手势，代替手指长按键盘空格
         * （vivo 输入法空格长按=讯飞语音输入）。需要无障碍服务已开启 + canPerformGestures。
         * 手势坐标是估算（键盘布局不公开），失败返回 false，由调用方提示手动长按。
         */
        fun spaceLongPress(): Boolean {
            val svc = instance ?: return false
            return try {
                val m = android.content.res.Resources.getSystem().displayMetrics
                val x = m.widthPixels * 0.5f
                val y = m.heightPixels * 0.935f // 键盘末排中央（空格键大致位置）
                val path = android.graphics.Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, 550
                )
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()
                svc.dispatchGesture(gesture, null, null)
            } catch (_: Exception) {
                false
            }
        }
    }
}
