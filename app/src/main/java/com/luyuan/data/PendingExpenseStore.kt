package com.luyuan.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 通知自动记账的「待确认」暂存（ledger-extras.html ①）：
 * 支付通知被监听后不直接入账，先落在这里；记账页顶部逐条 ✓入账 / ✕丢弃。
 * 契约红线：**存 App 私有 filesDir，不写共享目录、不进 SYNC_FORMAT**——
 * 只有用户点了「入账」才正式落 expense_<id>.json（走既有同步契约）。
 */
@Serializable
data class PendingExpense(
    val id: String,               // 8 位 hex，入账时即 expense_<id>.json 的文件 id
    val amount: Double,
    val merchant: String,
    val pkg: String,              // com.tencent.mm / com.eg.android.AlipayGphone
    val created_at: String,       // ISO 8601
    val category: String = "其他"
)

object PendingExpenseStore {

    private fun file(ctx: Context) = File(ctx.filesDir, "pending_expenses.json")

    fun list(ctx: Context): List<PendingExpense> = try {
        val f = file(ctx)
        if (!f.exists()) emptyList()
        else v2Json.decodeFromString(
            ListSerializer(PendingExpense.serializer()), f.readText(Charsets.UTF_8)
        ).sortedByDescending { it.created_at }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(ctx: Context, p: PendingExpense) {
        try {
            val cur = runCatching { list(ctx) }.getOrDefault(emptyList())
            file(ctx).writeText(
                v2Json.encodeToString(ListSerializer(PendingExpense.serializer()), cur + p)
            )
        } catch (_: Exception) {
        }
    }

    fun remove(ctx: Context, id: String) {
        try {
            val cur = list(ctx).filter { it.id != id }
            file(ctx).writeText(
                v2Json.encodeToString(ListSerializer(PendingExpense.serializer()), cur)
            )
        } catch (_: Exception) {
        }
    }

    /** 新建一条（id 在这里生成） */
    fun newId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "").take(8)
}
