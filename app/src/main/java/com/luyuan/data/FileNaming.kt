package com.luyuan.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 文件名约定（与 SYNC_FORMAT.md 一致）：YYYY-MM-DD_HH-MM-SS_<id前8位>.json
 * 例：2026-09-05_20-30-11_a1b2c3d4.json
 * 因 id 全局唯一，两端不会重名。
 */
object FileNaming {
    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun fileNameFor(noteId: String, createdAt: LocalDateTime = LocalDateTime.now()): String {
        val stamp = createdAt.format(FMT)
        val short = noteId.take(8)
        return "${stamp}_${short}.json"
    }

    /** 仅认普通记事文件，过滤 .sync-conflict 副本与无关文件 */
    fun isNoteFile(name: String): Boolean =
        name.endsWith(".json", ignoreCase = true) &&
                !name.contains(".sync-conflict")
}
