package com.luyuan.data

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import java.io.File

/**
 * 离线语音识别（实验）：sherpa-onnx 内置小模型，完全不出网。
 * - 模型 sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01（约 21MB，简体中文）
 *   由 CI 打进 APK assets（zipformer-zh-small-ctc/），与 sherpa-onnx v1.13.7 jniLib 配套
 * - 首次使用加载模型约 1-3 秒，之后常驻；识别失败抛异常，由调用方兜底
 *   （兜底 = 落库为「录音待转写」让电脑 SenseVoice 转写，原声永不丢）
 * - ⚠️ 2026-09-07 实测结论（勿回退大模型）：62MB transducer(2023训练) 人名/日常均不敌本
 *   21MB CTC(2025训练)；transducer 热词的 cjkchar 提分写法原生层解析失败被静默跳过，
 *   离线人名同音字错误靠 PC 端 fuzzy_scan_text 拼音兜底（同音必中）
 */
object OfflineStt {

    private const val MODEL_DIR = "zipformer-zh-small-ctc"
    private const val SAMPLE_RATE = 16000

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    /** 模型是否已随 APK 打包（未打包时录音页不显示离线模式入口） */
    fun bundled(context: Context): Boolean = try {
        context.assets.list(MODEL_DIR)?.contains("tokens.txt") == true
    } catch (_: Exception) {
        false
    }

    @Synchronized
    private fun getRecognizer(ctx: Context): OnlineRecognizer {
        recognizer?.let { return it }
        val config = OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = "$MODEL_DIR/model.int8.onnx"),
                tokens = "$MODEL_DIR/tokens.txt",
                numThreads = 2,
            ),
        )
        val rec = OnlineRecognizer(ctx.assets, config)
        recognizer = rec
        return rec
    }

    /** 预热：进离线模式时后台先加载模型，首次识别就不用等（失败静默，识别时再试） */
    @Synchronized
    fun preload(ctx: Context) {
        try {
            getRecognizer(ctx)
        } catch (_: Throwable) {
        }
    }

    /** 释放模型（内存紧张时可调；下次识别会重新加载） */
    @Synchronized
    fun release() {
        try {
            recognizer?.release()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    /**
     * 整段 wav（16k 单声道 16bit，AudioRecorder 产物）→ 文字。
     * 没听清返回空串；模型/文件异常抛异常。
     */
    fun transcribeWav(ctx: Context, wav: File): String {
        val samples = readPcm16(wav)
        if (samples.size < SAMPLE_RATE / 10) return "" // 不足 0.1 秒视为空
        val rec = getRecognizer(ctx)
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            // 0.3 秒静音尾巴：把最后一个字冲出来
            stream.acceptWaveform(FloatArray(SAMPLE_RATE * 3 / 10), SAMPLE_RATE)
            stream.inputFinished()
            while (rec.isReady(stream)) rec.decode(stream)
            return rec.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    /** 解析 16bit PCM mono wav → FloatArray(-1..1)。按 chunk 走，不写死 44 字节偏移 */
    private fun readPcm16(file: File): FloatArray {
        val bytes = file.readBytes()
        var pos = 12 // 跳过 RIFF 头
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val len = (bytes[pos + 4].toInt() and 0xFF) or
                    ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 7].toInt() and 0xFF) shl 24)
            if (id == "data") {
                dataOff = pos + 8
                dataLen = len
                break
            }
            pos += 8 + len + (len and 1)
        }
        if (dataOff < 0 || dataOff >= bytes.size) return FloatArray(0)
        val end = minOf(bytes.size, dataOff + dataLen)
        val n = (end - dataOff) / 2
        val out = FloatArray(n)
        var bi = dataOff
        for (i in 0 until n) {
            val lo = bytes[bi].toInt() and 0xFF
            val hi = bytes[bi + 1].toInt() and 0xFF
            // 先拼 16 位无符号，再用 Short 还原符号（避免符号扩展污染高位）
            val v = ((hi shl 8) or lo).toShort().toInt()
            out[i] = v / 32768f
            bi += 2
        }
        return out
    }
}
