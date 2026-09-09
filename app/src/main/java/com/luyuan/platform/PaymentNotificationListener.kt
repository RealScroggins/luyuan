package com.luyuan.platform

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.luyuan.data.PendingExpense
import com.luyuan.data.PendingExpenseStore
import java.time.OffsetDateTime

/**
 * 通知自动记账的监听端（ledger-extras.html ①，白板已拍板）：
 * 只盯微信/支付宝两个包名的通知，解析出金额才落「待确认」；解析保守、宁漏勿错——
 * 绝不见通知就入账（分级确认是全项目命门），真正入账永远在记账页由用户点 ✓。
 * 隐私红线：解析在本机完成，只存金额/商户/包名三类字段，账单原文不留盘。
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: return
            if (pkg != PKG_WECHAT && pkg != PKG_ALIPAY) return

            val ex = sbn.notification?.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val full = "$title $text $big"

            // 收入/非支出类直接跳过（宁可漏记不可错记）
            if (RX_SKIP.containsMatchIn(full)) return

            // 金额：¥12.00 / ￥3 / 付款12.50元（两个备选组，取非空那个）
            val m = RX_AMOUNT.find(full) ?: return
            val amount = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toDoubleOrNull() ?: return
            if (amount <= 0.0 || amount > 1_000_000.0) return

            // 商户：优先「¥12.00-兰芳园」，其次「向兰芳园付款」，兜底用支付渠道名
            val merchant = RX_MERCHANT_DASH.find(full)?.groupValues?.get(1)?.trim()
                ?: RX_MERCHANT_PAYTO.find(full)?.groupValues?.get(1)?.trim()
                ?: if (pkg == PKG_WECHAT) "微信支付" else "支付宝"

            // 60 秒内同金额+同商户去重（厂商会连发两条同内容通知）
            val nowIso = OffsetDateTime.now().toString()
            val nowMs = System.currentTimeMillis()
            val dup = PendingExpenseStore.list(this).any { p ->
                p.amount == amount && p.merchant == merchant && runCatching {
                    nowMs - OffsetDateTime.parse(p.created_at).toInstant().toEpochMilli() < 60_000
                }.getOrDefault(false)
            }
            if (dup) return

            PendingExpenseStore.add(
                this,
                PendingExpense(
                    id = PendingExpenseStore.newId(),
                    amount = amount,
                    merchant = merchant,
                    pkg = pkg,
                    created_at = nowIso
                )
            )
        } catch (_: Throwable) {
            // 通知解析永不干扰系统：任何异常静默吞掉
        }
    }

    companion object {
        const val PKG_WECHAT = "com.tencent.mm"
        const val PKG_ALIPAY = "com.eg.android.AlipayGphone"

        private val RX_AMOUNT = Regex("[¥￥]\\s*([0-9]+(?:\\.[0-9]+)?)|付款\\s*([0-9]+(?:\\.[0-9]+)?)\\s*元")
        private val RX_SKIP = Regex("收款|到账|已存入|退款|提现|红包|余额|理财|账单")
        private val RX_MERCHANT_DASH = Regex("[¥￥]\\s*[0-9]+(?:\\.[0-9]+)?\\s*[-–—]\\s*([^，,。\\n]{2,24})")
        private val RX_MERCHANT_PAYTO = Regex("向([^，,。\\n]{2,24})付款")

        /** 本 App 是否已拿到「通知使用权」（设置页/记账页引导用，API 24+ 系统 API） */
        fun enabled(ctx: android.content.Context): Boolean = try {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            nm?.isNotificationListenerAccessGranted(
                android.content.ComponentName(ctx, PaymentNotificationListener::class.java)
            ) ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
