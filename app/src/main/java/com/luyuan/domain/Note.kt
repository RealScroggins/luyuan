package com.luyuan.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 单条记事。字段严格对齐 D:\Luyuan\SYNC_FORMAT.md（跨端同步数据契约）。
 * - 电脑端现有笔记可能缺 updated_at/device/schema，读取时由 NoteRepository 做兜底兼容。
 * - ignoreUnknownKeys：容忍电脑端未来新增的字段。
 */
@Serializable
data class Note(
    val id: String,
    val created_at: String,
    // 电脑端 store.add_note 建的笔记没有 updated_at（SYNC_FORMAT 允许缺省），
    // 必须给默认值，否则 kotlinx.serialization 解析直接抛异常 → 整条笔记被丢弃。
    // 兜底逻辑（缺省视为 created_at）在 NoteRepository.readNote。
    val updated_at: String = "",
    val text: String,
    val source: String,            // "manual" | "voice"
    val tags: List<String> = emptyList(),
    val device: String = "phone",  // "pc" | "phone"
    val audio: String? = null,      // 相对路径 audio/<id>.wav
    val transcribed: Boolean? = null,
    val deleted: Boolean = false,   // 软删标记
    val schema: Int = 1
) {
    fun toJson(): String = noteJson.encodeToString(Note.serializer(), this)

    companion object {
        fun fromJson(s: String): Note = noteJson.decodeFromString(Note.serializer(), s)
    }
}

val noteJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}
