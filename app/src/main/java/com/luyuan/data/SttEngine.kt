package com.luyuan.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 本地语音识别引擎（Vosk）。模型较大，首次启动从官方地址下载并解压到内部存储，
 * 之后完全离线可用，契合「去中心化、不依赖电脑」目标。
 * 若模型下载/加载失败，createRecognizer 返回 null，调用方降级为「只录音不转写」。
 */
object SttEngine {
    private const val TAG = "SttEngine"
    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    private const val MODEL_DIR_NAME = "vosk-model-zh"

    private var model: Model? = null

    /** 模型下载进度（百分比）；-1 = 没在下载，100 = 下载完成（解压中） */
    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    /** 确保模型就绪；返回是否可用。失败不缓存，下次调用会重试下载。 */
    fun ensureModel(context: Context): Boolean {
        if (model != null) return true
        val base = File(context.getExternalFilesDir(null), MODEL_DIR_NAME)
        if (!modelReady(base)) {
            try {
                downloadAndUnzip(MODEL_URL, base)
            } catch (e: Exception) {
                Log.e(TAG, "model download failed: ${e.message}")
                _downloadProgress.value = -1
                return false
            }
        }
        _downloadProgress.value = -1
        return try {
            model = Model(findModelDir(base).absolutePath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "model load failed: ${e.message}")
            false
        }
    }

    /** 模型是否已解压就绪：存在 conf 或 am 目录即视为可用 */
    private fun modelReady(base: File): Boolean {
        val dir = findModelDir(base)
        return File(dir, "conf").exists() || File(dir, "am").exists()
    }

    fun createRecognizer(sampleRate: Float = 16000.0f): Recognizer? {
        val m = model ?: return null
        return try { Recognizer(m, sampleRate) } catch (e: Exception) { null }
    }

    private fun findModelDir(base: File): File {
        val subs = base.listFiles()?.filter { it.isDirectory } ?: return base
        return if (subs.size == 1) subs[0] else base
    }

    private fun downloadAndUnzip(url: String, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".zip")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.connect()
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                if (total > 0) {
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        _downloadProgress.value = ((done * 100) / total).toInt().coerceIn(0, 100)
                    }
                } else {
                    input.copyTo(out)
                }
            }
        }
        _downloadProgress.value = 100
        ZipInputStream(tmp.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                if (entry.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        tmp.delete()
    }
}
