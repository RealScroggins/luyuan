package com.luyuan.platform

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把未捕获异常写入共享目录 crash_log.txt——随 Syncthing 回传电脑，无需 adb 就能拿到崩溃现场。
 * 装 v0.9.1 起生效；出问题的复现一次，日志自动到电脑。
 */
object CrashLogger {
    private const val MAX = 200_000

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val root = StorageLocator.getRoot(appCtx)
                val f = File(root, "crash_log.txt")
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val line = "==== " +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()) +
                    " thread=" + t.name + "\n" + sw.toString() + "\n"
                val cur = if (f.exists() && f.length() < MAX) f.readText() else ""
                f.writeText((cur + line).takeLast(MAX))
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}
