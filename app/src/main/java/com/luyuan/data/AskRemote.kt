package com.luyuan.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 问路远手机版（开发计划终稿 §3.A2）：OpenAI 兼容 API 直连。
 * - Key/URL/模型只存本机 App 私有 SharedPreferences（隐私红线 §4.2，永不进同步目录/仓库）
 * - 上下文 = 本机笔记近期摘录 + 今日待办摘要（手机端不做 embedding）
 * - **私密标签笔记一律不外发**（隐私红线 §4.4）
 * - 会话历史只在内存里，不落 notes（不污染同步）
 */
object AskRemote {

    private const val PREFS = "ask_prefs"
    private const val DEFAULT_BASE = "https://api.deepseek.com"
    private const val DEFAULT_MODEL = "deepseek-chat"
    private const val PRIVATE_TAG = "私密"
    private const val MAX_CONTEXT_CHARS = 6000

    data class Config(val key: String, val baseUrl: String, val model: String) {
        val ready: Boolean get() = key.isNotBlank()
    }

    data class Turn(val role: String, val content: String)

    fun loadConfig(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            key = p.getString("key", "") ?: "",
            baseUrl = (p.getString("base", "") ?: "").ifBlank { DEFAULT_BASE },
            model = (p.getString("model", "") ?: "").ifBlank { DEFAULT_MODEL }
        )
    }

    fun saveConfig(context: Context, key: String, baseUrl: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("key", key.trim())
            .putString("base", baseUrl.trim().trimEnd('/'))
            .putString("model", model.trim())
            .apply()
    }

    /** 本机上下文：今日待办摘要 + 近期笔记摘录（排除私密）。超长截断，控 token。 */
    fun buildContext(context: Context): String {
        val sb = StringBuilder()
        // 今日待办（联系人待办 + 未触发提醒）
        val todoLines = mutableListOf<String>()
        try {
            for (c in ContactRepository.listContacts(context)) {
                for (t in c.undoneTodos) todoLines.add("${t.text}（${c.name}）")
            }
        } catch (_: Exception) {
        }
        try {
            for (n in NoteRepository.listNotes(context)) {
                if (PRIVATE_TAG in n.tags) continue
                if (!n.remind_at.isNullOrBlank() && n.remind_fired != true) {
                    todoLines.add("[提醒] ${n.text.take(40)}")
                }
            }
        } catch (_: Exception) {
        }
        if (todoLines.isNotEmpty()) {
            sb.appendLine("【未完成待办/提醒】")
            todoLines.take(20).forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }
        // 近期笔记摘录（最新在前，跳过私密）
        try {
            val notes = NoteRepository.listNotes(context)
                .filter { PRIVATE_TAG !in it.tags }
                .take(30)
            if (notes.isNotEmpty()) {
                sb.appendLine("【最近笔记摘录（新→旧）】")
                for (n in notes) {
                    val day = n.created_at.take(10)
                    val body = n.text.replace("\n", " ").take(80)
                    sb.appendLine("- [$day] $body")
                }
            }
        } catch (_: Exception) {
        }
        return if (sb.isBlank()) "（本机暂无笔记与待办）" else sb.toString().take(MAX_CONTEXT_CHARS)
    }

    /**
     * 调 OpenAI 兼容 chat/completions。返回回答文本；失败抛异常（message 已是人话）。
     * history：本轮对话的之前若干轮（仅内存，不落盘）。
     */
    fun ask(config: Config, question: String, history: List<Turn>, localContext: String): String {
        if (!config.ready) throw IllegalStateException("还没填 API Key，去设置页填一个")
        val messages = buildList {
            add(
                Turn(
                    "system",
                    "你是「路远」，路河的本地记事助手。回答要简洁、说人话。" +
                        "下面是用户本机的待办与笔记摘录，仅作参考；" +
                        "与问题无关就不要复述；摘录里没有的信息不要编造。\n\n$localContext"
                )
            )
            addAll(history.takeLast(8))
            add(Turn("user", question))
        }
        val payload = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.4)
            put("max_tokens", 1500)
            put("messages", buildJsonArray {
                for (m in messages) add(
                    buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    }
                )
            })
        }

        val conn = URL(config.baseUrl + "/chat/completions")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer " + config.key)
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException(humanError(code, body))
            }
            val root = Json.parseToJsonElement(body).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            return content?.trim().orEmpty().ifEmpty { "（模型返回了空回答）" }
        } finally {
            conn.disconnect()
        }
    }

    private fun humanError(code: Int, body: String): String = when (code) {
        401 -> "API Key 无效（去设置页检查一下）"
        402 -> "API 账户余额不足"
        404 -> "接口地址或模型名不对（检查设置页的 Base URL / 模型）"
        429 -> "请求太频繁或额度限流，稍后再试"
        else -> {
            val snippet = body.take(200).replace(Regex("\\s+"), " ")
            "请求失败（$code）：$snippet"
        }
    }
}
