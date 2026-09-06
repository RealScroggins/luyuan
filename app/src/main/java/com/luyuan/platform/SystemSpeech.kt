package com.luyuan.platform

import android.content.Context
import android.speech.SpeechRecognizer

/** 系统语音识别可用性（国内 ROM 通常内置讯飞系引擎，免费无密钥） */
object SystemSpeech {
    fun available(context: Context): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (_: Exception) {
        false
    }
}
