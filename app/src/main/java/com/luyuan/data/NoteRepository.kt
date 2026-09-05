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
        val dir = StorageLocator.notesDir(context)
        return (dir.listFiles { f -> FileNaming.isNoteFile(f.name) }
            ?.mapNotNull { readNote(it) }
            ?: emptyList())
            .filter { !it.deleted }
            .sortedByDescending { it.created_at }
    }

    fun getNote(context: Context, id: String): Note? {
        val dir = StorageLocator.notesDir(context)
        return dir.listFiles { f -> f.name.endsWith(".json", ignoreCase = true) }
            ?.mapNotNull { readNote(it) }
            ?.firstOrNull { it.id == id }
    }

    fun saveNote(context: Context, note: Note) {
        val dir = StorageLocator.notesDir(context)
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
