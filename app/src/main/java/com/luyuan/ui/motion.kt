package com.luyuan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import android.provider.Settings

/**
 * 全 App 唯一动效真源（施工合同 §三 时长表）。
 * 任何时长/曲线禁止在页面里写死，一律从这里取。
 */
object LuyuanMotion {
    val Fast = 150             // 按压、徽章、小控件
    val Move = 320             // 列表 stagger
    val StaggerItemDelay = 55  // stagger 每项延迟
    val StaggerOffsetPx = 14   // stagger 位移 14px→0
    val StaggerMax = 8         // 只做前 8 项
    val Morph = 360            // 页面切换
    val Count = 650            // 数值 count-up
    val Press = 80             // 按压缩放
    val PressScale = 0.96f
    val BarExpand = 800        // 分类占比条展开

    /** count-up 减速落定 1-(1-t)^3（easeOutCubic 近似） */
    val Decel = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    /** 弹簧（页面切换 / 撤回条弹入 / 左滑落位） */
    val Spring = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** 微弹（底栏指示 / 勾选 / toast 砸入） */
    val Micro = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    /** 流程（列表 stagger / 占比条展开） */
    val Flow = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/**
 * 系统「减少动画」开启（开发者选项 / 无障碍）→ 全 App 动效降级为瞬切。
 * Settings.Global.ANIMATOR_DURATION_SCALE == 0 即代表关闭动画，无需权限可读。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        try {
            Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 按压缩放：按下 scale→0.96（80ms），松开回 1。
 * - 传入已有 interactionSource：只做 graphicsLayer，不抢点击（与组件自身 clickable 共用同一 source）。
 * - 不传则自建 source（仅用于无点击但需按压缩放的装饰元素）。
 * 尊重「减少动画」：开启时跳过缩放。
 */
/**
 * 按压缩放：返回当前 scale（1f / PressScale）。调用处用 Modifier.graphicsLayer 应用。
 * - 传入已有 interactionSource：复用其按压事件（与组件自身 clickable / interactionSource 共用）。
 * - 不传则自建 source，但无按压源时恒定 1f（纯装饰元素慎用）。
 * 尊重「减少动画」：开启时恒定 1f。
 * 注：返回 Float 而非 Modifier，规避新版 Compose 已移除的 composed 工厂。
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource? = null,
    scale: Float = LuyuanMotion.PressScale
): Float {
    val reduce = rememberReduceMotion()
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(src) {
        src.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press ->
                    if (!reduce) anim.animateTo(scale, tween(LuyuanMotion.Press))
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    if (!reduce) anim.animateTo(1f, tween(LuyuanMotion.Press))
                else -> {}
            }
        }
    }
    return anim.value
}

/**
 * 数值 count-up：从 0 单向增长，650ms 减速落定。
 * 尊重「减少动画」：开启时直接显示终值不播。
 * @param target 终值；变化时重新从 0 增长（切月份/数据刷新会重播，符合预期）。
 * @param format 把中间 Double 渲染成字符串（金额需套 fmtMoney）。
 */
@Composable
fun CountUpText(
    target: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val reduce = rememberReduceMotion()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) {
            anim.snapTo(target.toFloat())
            return@LaunchedEffect
        }
        anim.animateTo(target.toFloat(), tween(LuyuanMotion.Count, easing = LuyuanMotion.Decel))
    }
    Text(
        text = format(anim.value.toDouble()),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}
