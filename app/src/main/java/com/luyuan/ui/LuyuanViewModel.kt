package com.luyuan.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luyuan.data.AudioRecorder
import com.luyuan.data.NoteRepository
import com.luyuan.data.SttEngine
import com.luyuan.domain.Note
import com.luyuan.platform.LuyuanService
import com.luyuan.platform.StorageLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.vosk.Recognizer
import java.io.File
import java.util.UUID

class LuyuanViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _trash = MutableStateFlow<List<Note>>(emptyList())
    val trash: StateFlow<List<Note>> = _trash

    private val _liveText = MutableStateFlow("")
    val liveText: StateFlow<String> = _liveText

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _sttReady = MutableStateFlow(false)
    val sttReady: StateFlow<Boolean> = _sttReady

    private val _sttMessage = MutableStateFlow("")
    val sttMessage: StateFlow<String> = _sttMessage

    private var recorder: AudioRecorder? = null
    private var recognizer: Recognizer? = null
    private var currentNoteId: String? = null
    private var currentAudioRel: String? = null

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SttEngine.ensureModel(ctx)
            _sttReady.value = ok
            if (!ok) _sttMessage.value = "语音模型下载失败（仅能录音）"
        }
    }

    /** 手动触发/重试加载语音模型（首次点录音或失败后调用） */
    private fun ensureStt() {
        if (_sttReady.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _sttMessage.value = "正在下载语音模型…"
            val ok = SttEngine.ensureModel(ctx)
            _sttReady.value = ok
            _sttMessage.value = if (ok) "语音模型就绪" else "语音模型下载失败（仅能录音）"
        }
    }

    fun refresh() {
        // 先同步置位再起协程：下拉刷新的指示器靠它联动，避免竞态提前收起
        _refreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _notes.value = NoteRepository.listNotes(ctx)
            _refreshing.value = false
        }
    }

    fun addManual(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.createManual(ctx, t)
            refresh()
        }
    }

    fun updateNote(id: String, text: String, tags: List<String> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.updateNote(ctx, id, text = text, tags = tags)
            refresh()
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.softDelete(ctx, id)
            refresh()
        }
    }

    fun refreshTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            _trash.value = NoteRepository.listTrash(ctx)
        }
    }

    fun restoreNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.restoreNote(ctx, id)
            _trash.value = NoteRepository.listTrash(ctx)
            _notes.value = NoteRepository.listNotes(ctx)
        }
    }

    fun purgeNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.purgeNote(ctx, id)
            _trash.value = NoteRepository.listTrash(ctx)
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        _liveText.value = ""
        ensureStt()
        recognizer = if (_sttReady.value) SttEngine.createRecognizer() else null
        val id = UUID.randomUUID().toString()
        val wav = File(StorageLocator.audioDir(ctx), "$id.wav")
        currentNoteId = id
        currentAudioRel = "audio/$id.wav"
        recorder = AudioRecorder(wav) { chunk ->
            recognizer?.let { rec ->
                try {
                    rec.acceptWaveForm(chunk, chunk.size)
                    val partial = rec.partialResult
                    val t = extractText(partial, "partial")
                    if (t.isNotBlank()) _liveText.value = t
                } catch (_: Exception) {
                }
            }
        }
        recorder?.start()
        _isRecording.value = true
        val intent = Intent(ctx, LuyuanService::class.java).apply {
            putExtra(LuyuanService.EXTRA_TEXT, "说话中…")
        }
        ctx.startForegroundService(intent)
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        recorder?.stop()
        recorder = null
        val finalText = try {
            recognizer?.finalResult?.let { extractText(it, "text") } ?: _liveText.value
        } catch (_: Exception) {
            _liveText.value
        }
        _isRecording.value = false
        ctx.stopService(Intent(ctx, LuyuanService::class.java))

        val text = finalText.ifBlank { _liveText.value }
        val noteId = currentNoteId
        val audioRel = currentAudioRel
        currentNoteId = null
        currentAudioRel = null

        viewModelScope.launch(Dispatchers.IO) {
            if (text.isNotBlank() && noteId != null) {
                val now = NoteRepository.nowIso()
                val note = Note(
                    id = noteId,
                    created_at = now,
                    updated_at = now,
                    text = text,
                    source = "voice",
                    tags = emptyList(),
                    device = "phone",
                    audio = audioRel,
                    transcribed = true,
                    schema = 1
                )
                NoteRepository.saveNote(ctx, note)
            }
            refresh()
        }
    }

    private fun extractText(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        val m = pattern.find(json) ?: return ""
        return m.groupValues[1]
            .replace("\\\\\"", "\"")
            .replace("\\\\\\\\", "\\\\")
    }
}
