package com.luyuan.platform

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.luyuan.MainActivity

/**
 * 无障碍服务全局按键监听（安卓唯一合法的全局按键通道，无需 adb）。
 * 唤录音组合键（路河 2026-09-10 拍板）：**同时按「音量加 + 音量减」**。
 * 旧「电源键+音量加」弃用（部分 ROM 不下发电源键事件给第三方，实锤无反应）。
 * 后台拉起界面依赖「显示在其他应用上层」权限（Settings.canDrawOverlays）。
 */
class VolumeKeyService : AccessibilityService() {

    private var volUpDown = false
    private var volDownDown = false

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
        // 长按的 repeat 连发不算（修"调音量误触"根因）
        if (event.repeatCount > 0) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (down) {
                    volUpDown = true
                    if (volDownDown) { fire(); return true }
                } else if (up) {
                    volUpDown = false
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (down) {
                    volDownDown = true
                    if (volUpDown) { fire(); return true }
                } else if (up) {
                    volDownDown = false
                }
            }
        }
        return false
    }

    /** 两键同按成功：消费按键（不让音量变）并唤起录音 */
    private fun fire() {
        volUpDown = false
        volDownDown = false
        launchRecord()
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
        var running = false
            private set

        @Volatile
        private var instance: VolumeKeyService? = null

        /**
         * 「双麦克风」之二：对键盘空格键做一次长按手势，代替手指长按空格
         * （vivo 输入法空格长按=讯飞语音输入）。需要无障碍服务已开启。
         * 定位：优先读输入法窗口的真实边界（空格在底排中央 ≈ 键盘高度 84%），
         * 读不到再退回屏幕比例估算。长按 1.5 秒触发。
         */
        fun spaceLongPress(): Boolean {
            val svc = instance ?: return false
            return try {
                val m = android.content.res.Resources.getSystem().displayMetrics
                var x = m.widthPixels * 0.5f
                var y = m.heightPixels * 0.88f
                try {
                    var ime: android.graphics.Rect? = null
                    for (w in svc.windows) {
                        if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                            ime = android.graphics.Rect()
                            w.getBoundsInScreen(ime)
                            break
                        }
                    }
                    ime?.let { r ->
                        if (r.height() > 200) {
                            x = r.exactCenterX()
                            y = r.top + r.height() * 0.84f
                        }
                    }
                } catch (_: Exception) {
                }
                val path = android.graphics.Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, 1500
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
