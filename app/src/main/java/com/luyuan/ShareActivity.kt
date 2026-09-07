package com.luyuan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.luyuan.data.NoteRepository
import java.util.regex.Pattern

/**
 * 系统分享接收：任意 App 里「分享 → 路远」，把文字一键存成记事（灵感.md App 清单项）。
 * 透明无界面：存完 toast 即退出，不进主界面（极简原则：一步完成）。
 *
 * 微信文章分享只给「标题 + 链接」（平台限制），存完立刻落库保证内容不丢，
 * 随后后台抓取文章正文补全同一条笔记（best effort：抓不到就保留原样）。
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) {
            Toast.makeText(this, "没有可保存的文字", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        var savedId: String? = null
        try {
            // tag「分享」标记来源；PC 端智能归类会另行追加分类标签，两者并存
            savedId = NoteRepository.createManual(this, body, tags = listOf("分享")).id
            Toast.makeText(this, "✅ 已存入路远", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
        finish()
        // 链接类分享（微信文章/网页）：后台抓正文补全，失败不影响已保存的链接
        val url = URL_PATTERN.matcher(body).let { m ->
            if (m.find()) m.group() else null
        }
        if (savedId != null && url != null) {
            val ctx = applicationContext
            Thread { fetchArticleInto(ctx, savedId, body, url) }.start()
        }
    }

    private fun fetchArticleInto(ctx: Context, noteId: String, original: String, url: String) {
        try {
            val doc = org.jsoup.Jsoup.connect(url)
                .timeout(9000)
                .userAgent(
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .followRedirects(true)
                .get()
            val title = listOf(
                doc.selectFirst("#activity-name"),                 // 微信公众号文章标题
                doc.selectFirst("h1"),
                doc.selectFirst("meta[property=og:title]")
            ).firstOrNull { it != null && it.text().isNotBlank() }
                ?.let { if (it.hasAttr("content")) it.attr("content") else it.text() }
                ?.trim()
                .orEmpty()
            val contentEl = listOf(
                doc.selectFirst("#js_content"),                    // 微信正文
                doc.selectFirst("article"),
                doc.selectFirst("main"),
                doc.body()
            ).firstOrNull { it != null && it.text().length > 200 }
            val content = contentEl?.wholeText()
                ?.replace(Regex("\n{3,}"), "\n\n")
                ?.trim()
                .orEmpty()
            if (content.length < 200) return // 没抓到有效正文，保留原样（标题+链接）
            val sb = StringBuilder()
            if (title.isNotBlank()) sb.append(title).append("\n\n")
            sb.append(if (content.length > 30000) content.take(30000) + "…(过长截断)" else content)
            sb.append("\n\n原文链接：").append(url)
            NoteRepository.updateNote(ctx, noteId, text = sb.toString())
        } catch (_: Exception) {
            // 网络/反爬/超时：保持原样（标题+链接），正文可随时用电脑端补
        }
    }

    private companion object {
        val URL_PATTERN: Pattern = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"
        )
    }
}
