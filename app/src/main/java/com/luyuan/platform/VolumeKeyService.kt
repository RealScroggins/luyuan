package com.luyuan.platform

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.luyuan.MainActivity

/**
 * 无障碍服务全局按键监听（安卓唯一合法的全局按键通道，无需 adb）。
 * 唤录音组合键（路河 2026-09-08 拍板）：**电源键 → 800ms 内按音量加**。
 * 旧「双击音量减」已弃用（误触率高：长按调音量时系统连发 ACTION_DOWN 被当成双击）。
 * 后台拉起界面依赖「显示在其他应用上层」权限（Settings.canDrawOverlays）。
 * ⚠️ 已知风险：部分 ROM 不把 KEYCODE_POWER 下发给第三方无障碍服务——若组合键无反应，
 * 兜底方案=三连音量加（晨报已登记，等真机反馈）。
 */
class VolumeKeyService : AccessibilityService() {

    private var lastPowerAt = 0L

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
        // 只认首次按下：长按的 repeat 连发不算（修"调音量误触"根因）
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val now = SystemClock.uptimeMillis()
        when (event.keyCode) {
            KeyEvent.KEYCODE_POWER -> lastPowerAt = now

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (now - lastPowerAt < COMBO_WINDOW_MS) {
                    lastPowerAt = 0L
                    launchRecord()
                    return true // 组合成功，消费按键不让音量变
                }
            }
        }
        return false
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
        /** 电源键按下后在此窗口内按音量加=唤录音 */
        private const val COMBO_WINDOW_MS = 800L
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
