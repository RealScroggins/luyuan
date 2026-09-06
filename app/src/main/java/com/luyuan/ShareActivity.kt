package com.luyuan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.luyuan.data.NoteRepository

/**
 * 系统分享接收：任意 App 里「分享 → 路远」，把文字一键存成记事（灵感.md App 清单项）。
 * 透明无界面：存完 toast 即退出，不进主界面（极简原则：一步完成）。
 * 仅收 text/plain（微信文章链接、复制的段落等都走这个）；图片分享暂不支持。
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
        } else {
            try {
                // tag「分享」标记来源；PC 端智能归类会另行追加分类标签，两者并存
                NoteRepository.createManual(this, body, tags = listOf("分享"))
                Toast.makeText(this, "✅ 已存入路远", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
