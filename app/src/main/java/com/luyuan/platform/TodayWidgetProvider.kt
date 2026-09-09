package com.luyuan.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.luyuan.MainActivity
import com.luyuan.R
import com.luyuan.data.ContactRepository
import com.luyuan.data.Course
import com.luyuan.data.NoteRepository
import com.luyuan.data.V2EntityRepository
import com.luyuan.domain.Note
import java.util.Calendar
import java.util.Locale

/**
 * 桌面小部件「路远·今日卡」：下一节课 / 今天待办 / 本月支出一眼看全，
 * 4×3 时多显示「最近 3 条」；底部记一笔（桌面速记）/ 说一句（进录音）。
 * 静态部件：updatePeriodMillis=30 分钟（系统兜底自愈），另在添加、重启、调尺寸、或 App 发 WIDGET_REFRESH 时重算。
 * 深链：课程块→page=course，待办→page=notes，支出→page=ledger，最近条→detail/{id}。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) renderSafe(context, manager, id)
    }

    /** 拖动调整组件大小（4×2 ↔ 4×3）时重算「最近 3 条」显隐 */
    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        renderSafe(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            for (id in ids) renderSafe(context, mgr, id)
        }
    }

    /**
     * 安全渲染入口：widget 进程由系统直接拉起（不经过 MainActivity，CrashLogger 未安装），
     * 任何未捕获异常都会让桌面显示空白且不留日志。这里全量兜底——
     * 崩了也渲染「极简错误卡」，并把异常栈写进共享目录 widget_err.txt（随 Syncthing 回传电脑）。
     */
    private fun renderSafe(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        try {
            render(context, manager, appWidgetId)
        } catch (e: Throwable) {
            logWidgetError(context, e)
            try {
                val fallback = RemoteViews(context.packageName, R.layout.widget_error)
                fallback.setOnClickPendingIntent(
                    R.id.widget_error_root,
                    PendingIntent.getActivity(
                        context, 4301,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                manager.updateAppWidget(appWidgetId, fallback)
            } catch (_: Throwable) {
            }
        }
    }

    /** 异常栈落盘到共享目录（不依赖 UncaughtExceptionHandler） */
    private fun logWidgetError(context: Context, e: Throwable) {
        try {
            val f = java.io.File(StorageLocator.getRoot(context), "widget_err.txt")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val prev = if (f.exists() && f.length() < 100_000) f.readText() else ""
            f.writeText(
                (prev + "==== " + java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(java.util.Date()) + "\n" + sw + "\n").takeLast(100_000)
            )
        } catch (_: Throwable) {
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        // 数据逐项防御：任一仓库异常只丢该项数据，不拖垮整卡渲染
        val courses = try { V2EntityRepository.listCourses(context) } catch (_: Throwable) { emptyList() }
        val contacts = try { ContactRepository.listContacts(context) } catch (_: Throwable) { emptyList() }
        val notes = try { NoteRepository.listNotes(context) } catch (_: Throwable) { emptyList() }
        val expenses = try { V2EntityRepository.listExpenses(context) } catch (_: Throwable) { emptyList() }

        // 日期
        views.setTextViewText(
            R.id.widget_date,
            try {
                val f = java.text.SimpleDateFormat("M月d日 E", Locale.CHINA)
                f.format(java.util.Date())
            } catch (_: Exception) { "" }
        )

        // 下一节课
        val next = nextCourse(courses)
        if (next != null) {
            views.setTextViewText(R.id.widget_course_hint, "下一节 · ${next.start}")
            views.setTextViewText(R.id.widget_course_name, next.name.ifBlank { "课程" })
            views.setTextViewText(
                R.id.widget_course_place,
                listOfNotNull(
                    next.place.takeIf { it.isNotBlank() },
                    next.teacher.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            )
            val mins = minutesUntil(next)
            views.setTextViewText(
                R.id.widget_course_countdown,
                if (next.weekday == todayLuyuan() && mins >= 0) "${mins}′ 后上课" else weekdayName(next.weekday)
            )
        } else {
            views.setTextViewText(R.id.widget_course_hint, "课程")
            views.setTextViewText(R.id.widget_course_name, "暂无课表")
            views.setTextViewText(R.id.widget_course_place, "去课程页添加")
            views.setTextViewText(R.id.widget_course_countdown, "")
        }

        // 今天待办 / 本月支出
        val undone = contacts.sumOf { c -> c.undoneTodos.size }
        val overdue = contacts.sumOf { c -> c.todos.count { t -> !t.done && isOverdue(t.remind_at) } }
        views.setTextViewText(
            R.id.widget_todo_val,
            if (overdue > 0) "$undone 件 · $overdue 过期" else "$undone 件"
        )
        val monthSum = expenses.filter { isThisMonth(it.spent_at.ifBlank { it.created_at }) }
            .sumOf { it.amount }
        views.setTextViewText(R.id.widget_expense_val, "¥${String.format(Locale.US, "%.1f", monthSum)}")

        // 最近 3 条（仅 4×3 显示）
        val recent = notes.sortedByDescending { it.updated_at.ifBlank { it.created_at } }.take(3)
        val h = manager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val showRecent = h >= RECENT_HEIGHT_THRESHOLD
        views.setViewVisibility(R.id.widget_recent, if (showRecent) View.VISIBLE else View.GONE)
        if (showRecent) {
            for (i in 0..2) {
                val rowId = recentRowId(i)
                val txId = recentTextId(i)
                val tmId = recentTimeId(i)
                if (i < recent.size) {
                    val n = recent[i]
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(txId, n.text.take(40))
                    views.setTextViewText(tmId, md(n.updated_at.ifBlank { n.created_at }))
                    views.setOnClickPendingIntent(rowId, detailPi(context, n.id))
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }
        }

        // 点击
        views.setOnClickPendingIntent(R.id.widget_course, pagePi(context, "course", 4101))
        views.setOnClickPendingIntent(R.id.widget_todo, pagePi(context, "notes", 4102))
        views.setOnClickPendingIntent(R.id.widget_expense, pagePi(context, "ledger", 4103))
        views.setOnClickPendingIntent(R.id.widget_note, quickNotePi(context))
        views.setOnClickPendingIntent(R.id.widget_record, recordPi(context))

        manager.updateAppWidget(appWidgetId, views)
    }

    // ---------- 数据计算 ----------

    private fun todayLuyuan(): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    private fun nextCourse(courses: List<Course>): Course? {
        if (courses.isEmpty()) return null
        val today = todayLuyuan()
        val nowMin = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 +
                Calendar.getInstance().get(Calendar.MINUTE)
        // 今天尚未开始的课里最早的一节
        courses.filter { it.weekday == today && toMin(it.start) >= nowMin }
            .minByOrNull { toMin(it.start) }?.let { return it }
        // 往后顺延到最近有课的那天
        for (delta in 1..7) {
            val wd = (today + delta - 1) % 7 + 1
            courses.filter { it.weekday == wd }.minByOrNull { toMin(it.start) }?.let { return it }
        }
        return null
    }

    private fun toMin(s: String): Int {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 0
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun minutesUntil(c: Course): Int {
        val nowMin = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 +
                Calendar.getInstance().get(Calendar.MINUTE)
        return toMin(c.start) - nowMin
    }

    private fun weekdayName(wd: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(wd - 1) { "" }

    private fun isOverdue(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        return try {
            java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() < System.currentTimeMillis()
        } catch (_: Exception) {
            false
        }
    }

    private fun isThisMonth(s: String): Boolean {
        if (s.length < 7) return false
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        return s.startsWith(String.format(Locale.US, "%04d-%02d", y, m))
    }

    private fun md(s: String): String = if (s.length >= 10) s.substring(5, 10) else s

    // ---------- PendingIntent ----------

    private fun pagePi(context: Context, page: String, req: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).putExtra("page", page)
        return PendingIntent.getActivity(
            context, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun detailPi(context: Context, id: String): PendingIntent {
        val i = Intent(context, MainActivity::class.java).putExtra("detailId", id)
        return PendingIntent.getActivity(
            context, 5000 + id.hashCode().let { if (it < 0) -it else it } % 1000,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun quickNotePi(context: Context): PendingIntent {
        val i = Intent(context, FloatingQuickNoteService::class.java)
        return PendingIntent.getService(
            context, 4201, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun recordPi(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java).putExtra("auto", "record")
        return PendingIntent.getActivity(
            context, 4202, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun recentRowId(i: Int) = when (i) {
        0 -> R.id.widget_recent_0; 1 -> R.id.widget_recent_1; else -> R.id.widget_recent_2
    }

    private fun recentTextId(i: Int) = when (i) {
        0 -> R.id.widget_recent_tx_0; 1 -> R.id.widget_recent_tx_1; else -> R.id.widget_recent_tx_2
    }

    private fun recentTimeId(i: Int) = when (i) {
        0 -> R.id.widget_recent_tm_0; 1 -> R.id.widget_recent_tm_1; else -> R.id.widget_recent_tm_2
    }

    companion object {
        const val ACTION_REFRESH = "com.luyuan.WIDGET_REFRESH"
        private const val RECENT_HEIGHT_THRESHOLD = 160
    }
}
