package com.luyuan.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luyuan.data.Contact
import com.luyuan.data.ContactRepository
import com.luyuan.data.NoteRepository
import com.luyuan.domain.Note
import com.luyuan.platform.JournalReminder
import com.luyuan.platform.ReminderScheduler
import com.luyuan.platform.StorageLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class LuyuanViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    /** 今天的日记（日记页编辑/语音归档用；主列表不含日记） */
    private val _todayDiary = MutableStateFlow<Note?>(null)
    val todayDiary: StateFlow<Note?> = _todayDiary

    /** 联系人（contacts/ 目录，与 PC 端人脉页互通） */
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** 每完成一次刷新 +1：下拉刷新的指示器靠它收回（refreshing 布尔会被撞帧合并吞掉，代际不会） */
    private val _refreshDone = MutableStateFlow(0)
    val refreshDone: StateFlow<Int> = _refreshDone

    private val _trash = MutableStateFlow<List<Note>>(emptyList())
    val trash: StateFlow<List<Note>> = _trash

    /** 心情时间线开关（本地偏好记忆，默认开） */
    private val moodPrefs = ctx.getSharedPreferences("luyuan_prefs", Context.MODE_PRIVATE)
    private val _moodEnabled = MutableStateFlow(moodPrefs.getBoolean("mood_enabled", true))
    val moodEnabled: StateFlow<Boolean> = _moodEnabled

    // ---------- 日记提醒（负一屏） ----------

    private val _journalEnabled = MutableStateFlow(JournalReminder.isEnabled(ctx))
    val journalEnabled: StateFlow<Boolean> = _journalEnabled

    private val _journalTime = MutableStateFlow(JournalReminder.time(ctx))
    val journalTime: StateFlow<Pair<Int, Int>> = _journalTime

    // ---------- 语音（系统识别为主，键盘兜底） ----------

    private val _liveText = MutableStateFlow("")
    val liveText: StateFlow<String> = _liveText

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    /** 本次是否归入今天的日记 */
    private val _diaryMode = MutableStateFlow(false)
    val diaryMode: StateFlow<Boolean> = _diaryMode

    /** 识别出错信息（null=无错）；"unavailable" 表示系统识别用不了，应切键盘 */
    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError

    /** 最近一次成功保存的内容（用于「已记下」反馈） */
    private val _savedMsg = MutableStateFlow("")
    val savedMsg: StateFlow<String> = _savedMsg

    private var speech: SpeechRecognizer? = null
    private var currentDiary = false

    init {
        refresh()
        // 接管电脑端同步过来的提醒（含错过补弹）+ 日记提醒；开机由 BootReceiver 兜底
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.rescheduleAll(ctx)
            JournalReminder.reschedule(ctx)
        }
    }

    fun refresh() {
        // 先同步置位再起协程：下拉刷新的指示器靠它联动，避免竞态提前收起
        _refreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _notes.value = NoteRepository.listNotes(ctx)
            _todayDiary.value = NoteRepository.todayDiaryNote(ctx)
            _contacts.value = ContactRepository.listContacts(ctx)
            _refreshing.value = false
            _refreshDone.value += 1
        }
    }

    fun toggleMood() {
        val next = !_moodEnabled.value
        moodPrefs.edit().putBoolean("mood_enabled", next).apply()
        _moodEnabled.value = next
    }

    fun setJournalEnabled(on: Boolean) {
        JournalReminder.setEnabled(ctx, on)
        _journalEnabled.value = on
    }

    fun setJournalTime(hour: Int, minute: Int) {
        JournalReminder.setTime(ctx, hour, minute)
        _journalTime.value = hour to minute
    }

    fun setReminder(id: String, remindAtIso: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.setReminder(ctx, id, remindAtIso)
            ReminderScheduler.cancel(ctx, id)
            ReminderScheduler.rescheduleAll(ctx)
            _notes.value = NoteRepository.listNotes(ctx)
        }
    }

    fun toggleContactTodo(contactId: String, todoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ContactRepository.toggleTodo(ctx, contactId, todoId)
            _contacts.value = ContactRepository.listContacts(ctx)
        }
    }

    // ---------- 记事 / 日记 ----------

    fun addManual(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.createManual(ctx, t)
            refresh()
        }
    }

    fun saveDiary(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            NoteRepository.saveDiary(ctx, t)
            refresh()
        }
    }

    fun saveDictation(text: String, diary: Boolean) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (diary) NoteRepository.saveDiary(ctx, t) else NoteRepository.createManual(ctx, t)
            _savedMsg.value = t
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

    /** 批量软删（多选删除，进回收站可恢复，与 PC 端语义一致） */
    fun deleteMany(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (id in ids) NoteRepository.softDelete(ctx, id)
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

    // ---------- 语音识别：系统引擎（vivo 内置讯飞系），失败自动退键盘 ----------

    fun startRecording(diary: Boolean = false) {
        if (_isRecording.value) return
        currentDiary = diary
        _diaryMode.value = diary
        _liveText.value = ""
        _voiceError.value = null
        _savedMsg.value = ""

        val sr = try {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } catch (_: Exception) {
            null
        }
        if (sr == null) {
            _voiceError.value = "unavailable"
            return
        }
        speech = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: ""
                releaseRecognizer(sr)
                _isRecording.value = false
                viewModelScope.launch(Dispatchers.IO) { handleFinalText(text, currentDiary) }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let {
                    if (it.isNotBlank()) _liveText.value = it
                }
            }

            override fun onError(error: Int) {
                releaseRecognizer(sr)
                _isRecording.value = false
                _voiceError.value = when (error) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到说话（错误码 $error）"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再试一次（错误码 $error）"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限（错误码 $error）"
                    else -> {
                        // 本机没有可用的系统语音服务：记住，以后直接进键盘模式
                        moodPrefs.edit().putString("voice_mode", "dictation").apply()
                        "unavailable($error)"
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            sr.startListening(intent)
            _isRecording.value = true
        } catch (_: Exception) {
            releaseRecognizer(sr)
            _isRecording.value = false
            moodPrefs.edit().putString("voice_mode", "dictation").apply()
            _voiceError.value = "unavailable(start)"
        }
    }

    /** 点停止：让识别器把最后一段说完返回 */
    fun stopRecording() {
        try {
            speech?.stopListening()
        } catch (_: Exception) {
        }
    }

    /** 取消本次识别/录音（切模式时用） */
    fun cancelRecording() {
        speech?.let { releaseRecognizer(it) }
        wavRecorder?.stop()
        wavRecorder = null
        wavId = null
        _wavStartedAt.value = 0L
        try {
            ctx.stopService(Intent(ctx, com.luyuan.platform.LuyuanService::class.java))
        } catch (_: Exception) {
        }
        _isRecording.value = false
        _liveText.value = ""
    }

    private fun releaseRecognizer(sr: SpeechRecognizer) {
        try {
            sr.destroy()
        } catch (_: Exception) {
        }
        if (speech === sr) speech = null
    }

    /** 初始语音模式（auto=跟系统走 / dictation / record；本机系统识别失败过就直接录音待转写） */
    fun preferredVoiceMode(): String =
        moodPrefs.getString("voice_mode", "auto") ?: "auto"

    fun rememberVoiceMode(mode: String) {
        moodPrefs.edit().putString("voice_mode", mode).apply()
    }

    /** 给今天日记加一张图（相册/相机），压缩后放共享 images/ 目录 */
    fun addDiaryImage(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val rel = NoteRepository.importImage(ctx, uri) ?: return@launch
            val imgs = (_todayDiary.value?.images ?: emptyList()) + rel
            NoteRepository.setDiaryImages(ctx, imgs)
            refresh()
        }
    }

    /** 从今天日记移除一张配图（文件保留，仅去掉引用，与 PC 端行为一致） */
    fun removeDiaryImage(rel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val imgs = (_todayDiary.value?.images ?: emptyList()) - rel
            NoteRepository.setDiaryImages(ctx, imgs)
            refresh()
        }
    }

    // ---------- 贴纸（Iconify SVG：Fluent 3D / OpenMoji，下载缓存到共享 images/stickers/） ----------

    private val _stickerMsg = MutableStateFlow("")
    val stickerMsg: StateFlow<String> = _stickerMsg

    /** 下载贴纸到共享目录并作为配图插入今天日记；已缓存直接插 */
    fun insertSticker(pack: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = java.io.File(StorageLocator.getRoot(ctx), "images/stickers").apply { mkdirs() }
                val fname = "${pack}_${name}.svg"
                val f = java.io.File(dir, fname)
                if (!f.exists() || f.length() < 100L) {
                    val url = "https://api.iconify.design/$pack/$name.svg"
                    val data = java.net.URL(url).readBytes()
                    if (data.size < 100) throw IllegalStateException("empty svg")
                    f.writeBytes(data)
                }
                val rel = "images/stickers/$fname"
                val cur = _todayDiary.value?.images ?: emptyList()
                if (rel !in cur) {
                    NoteRepository.setDiaryImages(ctx, cur + rel)
                    refresh()
                }
                _stickerMsg.value = ""
            } catch (e: Exception) {
                _stickerMsg.value = "贴纸下载失败，检查网络后重试"
            }
        }
    }

    // ---------- 录音待转写：原声 wav 经 Syncthing 回电脑，SenseVoice 转写后同步回来 ----------

    private var wavRecorder: com.luyuan.data.AudioRecorder? = null
    private var wavId: String? = null

    private val _wavStartedAt = MutableStateFlow(0L)
    val wavStartedAt: StateFlow<Long> = _wavStartedAt

    fun startWavRecording(diary: Boolean = false) {
        if (_isRecording.value) return
        currentDiary = diary
        _diaryMode.value = diary
        _liveText.value = ""
        _voiceError.value = null
        _savedMsg.value = ""
        val id = UUID.randomUUID().toString()
        wavId = id
        _wavStartedAt.value = System.currentTimeMillis()
        wavRecorder = com.luyuan.data.AudioRecorder(
            java.io.File(StorageLocator.audioDir(ctx), "$id.wav")
        ) { }
        try {
            wavRecorder?.start()
            _isRecording.value = true
            val intent = Intent(ctx, com.luyuan.platform.LuyuanService::class.java).apply {
                putExtra(com.luyuan.platform.LuyuanService.EXTRA_TEXT, "录音中…")
            }
            ctx.startForegroundService(intent)
        } catch (_: Exception) {
            wavRecorder = null
            wavId = null
            _wavStartedAt.value = 0L
            _isRecording.value = false
            _voiceError.value = "录音启动失败（检查麦克风权限）"
        }
    }

    fun stopWavRecording() {
        wavRecorder?.stop()
        wavRecorder = null
        try {
            ctx.stopService(Intent(ctx, com.luyuan.platform.LuyuanService::class.java))
        } catch (_: Exception) {
        }
        _isRecording.value = false
        _wavStartedAt.value = 0L
        val id = wavId
        val diary = currentDiary
        wavId = null
        if (id == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = NoteRepository.nowIso()
            NoteRepository.saveNote(
                ctx,
                Note(
                    id = id,
                    created_at = now,
                    updated_at = now,
                    text = "（语音待转写）",
                    source = "voice",
                    tags = if (diary) listOf("日记") else emptyList(),
                    device = "phone",
                    audio = "audio/$id.wav",
                    transcribed = false,
                    schema = 1
                )
            )
            _savedMsg.value = "已录音，等电脑转写"
            refresh()
        }
    }

    // ---------- 离线识别（实验）：sherpa-onnx 内置小模型，无网转写；失败退回录音待转写 ----------

    private val _offlineBusy = MutableStateFlow(false)
    val offlineBusy: StateFlow<Boolean> = _offlineBusy

    /** APK 里是否打包了离线模型（决定录音页显示不显示该模式） */
    fun offlineBundled(): Boolean = com.luyuan.data.OfflineStt.bundled(ctx)

    /**
     * 离线模式点停止：录音先落 wav，本机转写成功 → 直接出文字落库（transcribed=true，原声保留）；
     * 识别不出 → 原样按「录音待转写」落库，电脑 SenseVoice 接手，内容永不丢。
     */
    fun stopOfflineRecording() {
        val id = wavId
        wavRecorder?.stop()
        wavRecorder = null
        try {
            ctx.stopService(Intent(ctx, com.luyuan.platform.LuyuanService::class.java))
        } catch (_: Exception) {
        }
        _isRecording.value = false
        _wavStartedAt.value = 0L
        if (id == null) return
        val diary = currentDiary
        wavId = null
        _offlineBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val now = NoteRepository.nowIso()
            val wav = java.io.File(StorageLocator.audioDir(ctx), "$id.wav")
            val text = try {
                com.luyuan.data.OfflineStt.transcribeWav(ctx, wav)
            } catch (t: Throwable) {
                // 必须兜 Throwable：JNI 库缺失/ABI 不符抛 UnsatisfiedLinkError（Error 家族），
                // 只接 Exception 会在离线模式闪退而不是退回录音待转写
                android.util.Log.e("OfflineStt", "offline transcribe failed", t)
                ""
            }.trim()
            if (text.isNotEmpty()) {
                NoteRepository.saveNote(
                    ctx,
                    Note(
                        id = id,
                        created_at = now,
                        updated_at = now,
                        text = text,
                        source = "voice",
                        tags = if (diary) listOf("日记") else emptyList(),
                        device = "phone",
                        audio = "audio/$id.wav",
                        transcribed = true,
                        schema = 1
                    )
                )
                _savedMsg.value = text
            } else {
                NoteRepository.saveNote(
                    ctx,
                    Note(
                        id = id,
                        created_at = now,
                        updated_at = now,
                        text = "（语音待转写）",
                        source = "voice",
                        tags = if (diary) listOf("日记") else emptyList(),
                        device = "phone",
                        audio = "audio/$id.wav",
                        transcribed = false,
                        schema = 1
                    )
                )
                _voiceError.value = "离线识别没出文字，已改为录音待电脑转写"
            }
            _offlineBusy.value = false
            refresh()
        }
    }

    /** 识别完成 → 落库（普通笔记 / 并入今天日记） */
    private suspend fun handleFinalText(text: String, diary: Boolean) {
        if (text.isBlank()) {
            _voiceError.value = "没听到说话"
            return
        }
        if (diary) {
            val existing = NoteRepository.todayDiaryNote(ctx)
            if (existing != null) {
                NoteRepository.updateNote(
                    ctx, existing.id,
                    text = (existing.text + "\n" + text).trim(),
                    tags = existing.tags
                )
            } else {
                val now = NoteRepository.nowIso()
                NoteRepository.saveNote(
                    ctx,
                    Note(
                        id = UUID.randomUUID().toString(),
                        created_at = now,
                        updated_at = now,
                        text = text,
                        source = "voice",
                        tags = listOf("日记"),
                        device = "phone",
                        transcribed = true,
                        schema = 1
                    )
                )
            }
        } else {
            val now = NoteRepository.nowIso()
            NoteRepository.saveNote(
                ctx,
                Note(
                    id = UUID.randomUUID().toString(),
                    created_at = now,
                    updated_at = now,
                    text = text,
                    source = "voice",
                    tags = emptyList(),
                    device = "phone",
                    transcribed = true,
                    schema = 1
                )
            )
        }
        _savedMsg.value = text
        refresh()
    }
}
