package com.luyuan.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.luyuan.MainActivity
import com.luyuan.R
import com.luyuan.data.Note
import com.luyuan.data.NoteRepository

/** 系统通知：提醒用高优先级渠道，点了跳进 App */
object ReminderNotifications {
    const val CHANNEL_ID = "luyuan_reminders"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "笔记提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun fire(context: Context, noteId: String, title: String, body: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, noteId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_mic)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(noteId.hashCode(), n)
        } catch (_: SecurityException) {
            // 无通知权限：静默跳过（设置页/录音页有引导授权）
        }
    }
}

/** 到点响铃 + 错过补弹的统一入口 */
private fun fireReminder(context: Context, note: Note) {
    ReminderNotifications.fire(context, note.id, "⏰ 提醒", note.text.take(200))
    NoteRepository.markReminderFired(context, note.id)
}

/**
 * 提醒调度：每条未触发的 remind_at 挂一个 AlarmManager 闹钟（最多 20 条最近即将到点的）。
 * 电脑端设的提醒随 Syncthing 同步过来，App 打开/开机时 rescheduleAll 即可接管。
 */
object ReminderScheduler {
    fun rescheduleAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val upcoming = NoteRepository.pendingReminders(context)
            .mapNotNull { n -> NoteRepository.remindAtMillis(n)?.let { n to it } }
            .filter { (_, at) -> at > now }
            .sortedBy { (_, at) -> at }
            .take(20)
        for ((note, at) in upcoming) {
            val pi = alarmIntent(context, note.id)
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60 * 1000L, pi)
            }
        }
        // 已过期但没响过的（电脑关机时到点/同步晚到）→ 立刻补弹
        val overdue = NoteRepository.pendingReminders(context)
            .mapNotNull { n -> NoteRepository.remindAtMillis(n)?.let { n to it } }
            .filter { (_, at) -> at <= now }
            .sortedBy { (_, at) -> at }
        for ((note, _) in overdue.take(10)) {
            fireReminder(context, note)
        }
    }

    fun cancel(context: Context, noteId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context, noteId))
    }

    private fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun alarmIntent(context: Context, noteId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("note_id", noteId)
        return PendingIntent.getBroadcast(
            context, noteId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 闹钟到点：响通知 → 标记已触发 → 排下一条 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("note_id") ?: return
        val note = NoteRepository.findAny(context, id) ?: return
        if (note.deleted || note.remind_fired == true) return
        fireReminder(context, note)
        rescheduleAll(context)
    }

    private fun rescheduleAll(context: Context) = ReminderScheduler.rescheduleAll(context)
}

/** 开机后重新挂闹钟（重启不丢提醒） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
