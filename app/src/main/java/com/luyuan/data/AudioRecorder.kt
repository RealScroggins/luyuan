package com.luyuan.data

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream

/**
 * 录制 16kHz 单声道 16-bit PCM：边录边把 PCM 块通过 onPcm 回调给 STT 引擎，
 * 录音结束后写成标准 WAV 文件（audio/<id>.wav），供电脑端复用。
 */
class AudioRecorder(
    private val outputWav: File,
    private val onPcm: (ByteArray) -> Unit
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var running = false

    fun start() {
        val sampleRate = 16000
        val chanCfg = AudioFormat.CHANNEL_IN_MONO
        val fmt = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, chanCfg, fmt)
        record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, chanCfg, fmt, minBuf * 2)
        record?.startRecording()
        running = true
        val pcmTmp = File(outputWav.parentFile, outputWav.name + ".pcm.tmp")
        val os = FileOutputStream(pcmTmp)
        thread = Thread {
            val buffer = ByteArray(minBuf)
            while (running) {
                val read = record?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    os.write(chunk)
                    onPcm(chunk)
                }
            }
            os.close()
            writeWav(outputWav, pcmTmp, sampleRate)
            pcmTmp.delete()
        }
        thread?.start()
    }

    fun stop() {
        running = false
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        thread?.join(2000)
        thread = null
    }

    private fun writeWav(out: File, pcm: File, sampleRate: Int) {
        val pcmBytes = pcm.readBytes()
        val total = pcmBytes.size
        val channels = 1
        val bitDepth = 16
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = channels * bitDepth / 8
        FileOutputStream(out).use { os ->
            fun le16(v: Int) { os.write(v and 0xFF); os.write((v ushr 8) and 0xFF) }
            fun le32(v: Int) {
                os.write(v and 0xFF); os.write((v ushr 8) and 0xFF)
                os.write((v ushr 16) and 0xFF); os.write((v ushr 24) and 0xFF)
            }
            fun ascii(s: String) = os.write(s.toByteArray(Charsets.US_ASCII))
            ascii("RIFF"); le32(36 + total); ascii("WAVE")
            ascii("fmt "); le32(16); le16(1); le16(channels)
            le32(sampleRate); le32(byteRate); le16(blockAlign); le16(bitDepth)
            ascii("data"); le32(total)
            os.write(pcmBytes)
        }
    }
}
