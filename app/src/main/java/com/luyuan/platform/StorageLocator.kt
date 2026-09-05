package com.luyuan.platform

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 定位 Luyuan 共享根目录。默认 /storage/emulated/0/Luyuan/，
 * 与电脑端 D:\Luyuan\data 通过 Syncthing 双向同步（目录结构一致）。
 * 依赖 MANAGE_EXTERNAL_STORAGE 权限直接读写该目录。
 */
object StorageLocator {
    private const val PREFS = "luyuan_prefs"
    private const val KEY_ROOT = "root_dir"
    private const val DEFAULT = "Luyuan"

    fun getRoot(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_ROOT, null) ?: DEFAULT
        return File(Environment.getExternalStorageDirectory(), name)
    }

    fun setRootName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ROOT, name).apply()
    }

    fun notesDir(context: Context): File =
        File(getRoot(context), "notes").also { it.mkdirs() }

    fun audioDir(context: Context): File =
        File(getRoot(context), "audio").also { it.mkdirs() }
}
