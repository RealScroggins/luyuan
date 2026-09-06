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

    /** 候选目录：绝对路径 + 里面扫到的笔记数（目录防呆选择器用） */
    data class Candidate(val path: String, val count: Int)

    fun getRoot(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_ROOT, null) ?: DEFAULT
        return File(Environment.getExternalStorageDirectory(), name).also { it.mkdirs() }
    }

    fun setRootName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ROOT, name).apply()
    }

    fun notesDir(context: Context): File =
        File(getRoot(context), "notes").also { it.mkdirs() }

    fun audioDir(context: Context): File =
        File(getRoot(context), "audio").also { it.mkdirs() }

    /** 扫描 /storage/emulated/0 顶层目录，挑出可能是共享目录的候选（含笔记或名字像路远） */
    fun candidates(context: Context): List<Candidate> {
        val ext = Environment.getExternalStorageDirectory()
        val cur = getRoot(context).absolutePath
        val out = mutableListOf<Candidate>()
        val dirs = ext.listFiles { f -> f.isDirectory } ?: return out
        for (d in dirs) {
            val n = countJsonFiles(d, 0, 3)
            val looksLuyuan = d.name.contains("路远") || d.name.lowercase().contains("luyuan")
            if (n > 0 || looksLuyuan || d.absolutePath == cur) {
                out.add(Candidate(d.absolutePath, n))
            }
        }
        out.sortByDescending { it.count }
        if (out.none { it.path == cur }) {
            out.add(0, Candidate(cur, countJsonFiles(File(cur), 0, 3)))
        }
        return out
    }

    /** 递归数 .json 笔记文件（跳过 .sync-conflict 副本与 audio 等无关目录不特殊处理，数量仅作参考） */
    fun countJsonFiles(dir: File?, depth: Int, maxDepth: Int): Int {
        if (dir == null || !dir.isDirectory || depth > maxDepth) return 0
        var n = 0
        for (f in dir.listFiles() ?: return 0) {
            if (f.isDirectory) {
                n += countJsonFiles(f, depth + 1, maxDepth)
            } else if (f.name.endsWith(".json", ignoreCase = true) &&
                !f.name.contains(".sync-conflict")
            ) n++
        }
        return n
    }
}
