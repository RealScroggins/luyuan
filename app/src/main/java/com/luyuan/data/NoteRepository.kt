package com.luyuan.data

import android.content.Context
import com.luyuan.domain.Note
import com.luyuan.domain.SyncPolicy
import com.luyuan.platform.StorageLocator
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * 记事读写层。严格按 SYNC_FORMAT.md：每笔记一个 JSON 文件，软删不物理删。
 * 兼容兜底：电脑端旧笔记缺 updated_at/device/schema 时按规范补默认值。
 */
object NoteRepository {

    fun listNotes(context: Context): List<Note> {
        return allDistinct(context).filter { !it.deleted }.sortedByDescending { it.created_at }
    }

    /** 回收站：只看软删的 */
    fun listTrash(context: Context): List<Note> {
        return allDistinct(context).filter { it.deleted }.sortedByDescending { it.updated_at }
    }

    /** 全量扫描 + 同 id 去重（取 updated_at 最新那份），列表/回收站共用 */
    private fun allDistinct(context: Context): List<Note> {
        val root = StorageLocator.getRoot(context)
        val found = mutableListOf<Note>()
        collectNotes(root, found, 0, 6)
        return found
            .sortedByDescending { it.updated_at.ifBlank { it.created_at } }
            .distinctBy { it.id }
    }

    /** 递归扫描共享根目录：无论 Syncthing 把笔记映射到哪一层（notes/、data/notes/、根目录直放）都能读到 */
    private fun collectNotes(dir: File?, out: MutableList<Note>, depth: Int, maxDepth: Int) {
        if (dir == null || !dir.isDirectory || depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                collectNotes(f, out, depth + 1, maxDepth)
            } else if (FileNaming.isNoteFile(f.name)) {
                readNote(f)?.let { out.add(it) }
            }
        }
    }

    fun getNote(context: Context, id: String): Note? {
        return listNotes(context).firstOrNull { it.id == id }
    }

    fun saveNote(context: Context, note: Note) {
        val root = StorageLocator.getRoot(context).also { it.mkdirs() }
        // 已存在则原地覆盖，绝不另起新文件名——否则同一 id 留下多份文件，
        // 经 Syncthing 同步到电脑端也变成重复文件
        val file = findFileById(root, note.id, 0, 6)
            ?: File(root, FileNaming.fileNameFor(note.id, parseCreatedAt(note.created_at)))
        file.writeText(note.toJson(), Charsets.UTF_8)
    }

    /** 按 id 前 8 位找已有文件（文件名约定 ..._<id前8位>.json），递归兼容旧层级 */
    private fun findFileById(dir: File, noteId: String, depth: Int, maxDepth: Int): File? {
        if (!dir.isDirectory || depth > maxDepth) return null
        val suffix = "_" + noteId.take(8).lowercase() + ".json"
        for (f in dir.listFiles() ?: return null) {
            if (f.isDirectory) {
                findFileById(f, noteId, depth + 1, maxDepth)?.let { return it }
            } else if (!f.name.contains(".sync-conflict") &&
                f.name.lowercase().endsWith(suffix)
            ) {
                return f
            }
        }
        return null
    }

    fun updateNote(
        context: Context,
        id: String,
        text: String? = null,
        tags: List<String>? = null
    ) {
        val existing = getNote(context, id) ?: return
        val updated = existing.copy(
            text = text ?: existing.text,
            tags = tags ?: existing.tags,
            updated_at = nowIso()
        )
        saveNote(context, updated)
    }

    /** 软删：置 deleted=true，不删文件 */
    fun softDelete(context: Context, id: String) {
        val existing = getNote(context, id) ?: return
        saveNote(context, existing.copy(deleted = true, updated_at = nowIso()))
    }

    /** 从回收站恢复：置回 deleted=false，updated_at 刷新使撤销经 Syncthing 传到电脑端 */
    fun restoreNote(context: Context, id: String) {
        val n = findAny(context, id) ?: return
        if (n.deleted) saveNote(context, n.copy(deleted = false, updated_at = nowIso()))
    }

    /** 彻底删除：物理删文件（不可恢复），与电脑端 purge 对齐 */
    fun purgeNote(context: Context, id: String) {
        val root = StorageLocator.getRoot(context)
        findFileById(root, id, 0, 6)?.delete()
    }

    /** 查找含已软删在内的任意一条（getNote 只看未删除的） */
    fun findAny(context: Context, id: String): Note? {
        return allDistinct(context).firstOrNull { it.id == id }
    }

    // ---------- 提醒（SYNC_FORMAT remind_at / remind_fired，两端互通） ----------

    /** 设提醒：remindAtIso 为 null = 取消。新提醒置 remind_fired=false。 */
    fun setReminder(context: Context, id: String, remindAtIso: String?) {
        val existing = findAny(context, id) ?: return
        saveNote(
            context,
            existing.copy(
                remind_at = remindAtIso,
                remind_fired = if (remindAtIso == null) existing.remind_fired else false,
                updated_at = nowIso()
            )
        )
    }

    /** 到点/错过补弹后标记已触发（防重复响） */
    fun markReminderFired(context: Context, id: String) {
        val n = findAny(context, id) ?: return
        if (n.remind_fired != true) {
            saveNote(context, n.copy(remind_fired = true, updated_at = nowIso()))
        }
    }

    /** 待提醒清单：未删 + 设了 remind_at + 还没响过（含已过期未响的，用于错过补弹） */
    fun pendingReminders(context: Context): List<Note> {
        return allDistinct(context).filter {
            !it.deleted && it.remind_fired != true && !it.remind_at.isNullOrBlank()
        }
    }

    /** remind_at → epoch 毫秒（带时区直接转，naive 按本地时区），解析失败返回 null */
    fun remindAtMillis(note: Note): Long? {
        val ra = note.remind_at ?: return null
        return try {
            OffsetDateTime.parse(ra).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(ra).atOffset(OffsetDateTime.now().offset)
                    .toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun createManual(
        context: Context,
        text: String,
        tags: List<String> = emptyList()
    ): Note {
        val now = nowIso()
        val note = Note(
            id = UUID.randomUUID().toString(),
            created_at = now,
            updated_at = now,
            text = text,
            source = "manual",
            tags = tags,
            device = SyncPolicy.DEVICE_PHONE,
            schema = SyncPolicy.SCHEMA_VERSION
        )
        saveNote(context, note)
        return note
    }

    private fun readNote(file: File): Note? = try {
        val raw = file.readText(Charsets.UTF_8)
        val n = Note.fromJson(raw)
        // 兼容兜底：缺字段补默认（updated_at 现已可缺省，这里统一补齐）
        n.copy(
            updated_at = if (n.updated_at.isBlank()) n.created_at else n.updated_at,
            device = if (n.device.isBlank()) SyncPolicy.DEVICE_PC else n.device,
            schema = if (n.schema == 0) SyncPolicy.SCHEMA_VERSION else n.schema
        )
    } catch (e: Exception) {
        null
    }

    /** created_at(ISO8601, 可带时区) → 本地时间，用于生成规范文件名；解析失败退回当前时间 */
    private fun parseCreatedAt(s: String): LocalDateTime = try {
        OffsetDateTime.parse(s).toLocalDateTime()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(s)
        } catch (_: Exception) {
            LocalDateTime.now()
        }
    }

    fun nowIso(): String {
        val offset = OffsetDateTime.now(ZoneId.systemDefault()).offset
        return LocalDateTime.now().atOffset(offset).toString()
    }
}
