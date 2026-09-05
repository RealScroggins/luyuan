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
        val root = StorageLocator.getRoot(context)
        val found = mutableListOf<Note>()
        collectNotes(root, found, 0, 6)
        return found
            .distinctBy { it.id }
            .filter { !it.deleted }
            .sortedByDescending { it.created_at }
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
        val dir = StorageLocator.getRoot(context).also { it.mkdirs() }
        val file = File(dir, FileNaming.fileNameFor(note.id))
        file.writeText(note.toJson(), Charsets.UTF_8)
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
        // 兼容兜底：缺字段补默认
        n.copy(
            updated_at = if (n.updated_at.isBlank()) n.created_at else n.updated_at,
            device = if (n.device.isBlank()) SyncPolicy.DEVICE_PC else n.device,
            schema = if (n.schema == 0) SyncPolicy.SCHEMA_VERSION else n.schema
        )
    } catch (e: Exception) {
        null
    }

    fun nowIso(): String {
        val offset = OffsetDateTime.now(ZoneId.systemDefault()).offset
        return LocalDateTime.now().atOffset(offset).toString()
    }
}
