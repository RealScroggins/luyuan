package com.luyuan.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.luyuan.MainActivity
import com.luyuan.R

/**
 * 桌面小部件「路远·随手记」：仿输入框样式——
 * ✏️ 记一笔… = 打开主页并聚焦输入框弹键盘（auto=note）；🎙️ = 直接进录音页开录（auto=record）。
 * 静态部件：不需要周期刷新（updatePeriodMillis=0），只在添加/重启时布局一次。
 */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick)

        // 「记一笔」输入区：拉起悬浮速记条，在桌面上直接打字保存（不开 App）
        val notePi = PendingIntent.getService(
            context, 3001,
            Intent(context, FloatingQuickNoteService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val recPi = PendingIntent.getActivity(
            context, 3002,
            Intent(context, MainActivity::class.java).putExtra("auto", "record"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_note, notePi)
        views.setOnClickPendingIntent(R.id.widget_record, recPi)
        manager.updateAppWidget(ids, views)
    }
}
