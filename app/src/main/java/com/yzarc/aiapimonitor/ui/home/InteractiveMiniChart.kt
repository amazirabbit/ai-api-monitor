package com.yzarc.aiapimonitor.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yzarc.aiapimonitor.data.repository.ChartPoint
import kotlin.math.roundToInt

/** 绘图文字画笔 */
fun chartPaint(hex: Long, size: Float, bold: Boolean = false): android.graphics.Paint =
    android.graphics.Paint().also {
        it.color = hex.toInt(); it.textSize = size
        it.isAntiAlias = true; it.isFakeBoldText = bold
    }

@Composable
fun InteractiveMiniChart(
    data: List<ChartPoint>,
    lineColor: Color,
    fillColor: Color,
    gridColor: Color,
    dotBg: Color
) {
    if (data.size < 2) return
    var hoverIdx by remember { mutableIntStateOf(-1) }
    val crosshairColor = lineColor.copy(alpha = 0.25f)
    val tooltipBg = Color(0xFF2C2218)

    // 修复 7：Y 轴动态范围
    val values = data.map { it.value }
    val maxVal = values.maxOrNull()?.let { if (it == 0.0) 1.0 else it * 1.15 } ?: 1.0
    val minVal = values.minOrNull()?.coerceAtMost(0.0) ?: 0.0
    // 修复 1：防 range=0
    val range = (maxVal - minVal).takeIf { it > 0 } ?: 1.0
    val padL = 36f; val padR = 4f; val padT = 4f; val padB = 22f

    var boxW by remember { mutableIntStateOf(0) }; var boxH by remember { mutableIntStateOf(0) }
    // 修复 2：tooltip 动态尺寸
    var tipSize by remember { mutableStateOf(IntOffset.Zero) }
    // 修复 6：固定吸附半径
    val density = LocalDensity.current
    val boxPadPx = with(density) { 4.dp.toPx() }
    val hitRadiusPx = with(density) { 24.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .onSizeChanged { boxW = it.width; boxH = it.height }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(data) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pos = ev.changes.firstOrNull()?.position
                            if (pos != null && data.size > 1) {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (pos.x < padL || pos.x > w - padR ||
                                    pos.y < padT || pos.y > h - padB) {
                                    hoverIdx = -1
                                } else {
                                    val cw = w - padL - padR
                                    // 修复 3：O(1) 最近点 + 固定吸附半径(修复6)
                                    val nextHover = (((pos.x - padL) / cw) * (data.size - 1))
                                        .roundToInt()
                                        .coerceIn(0, data.lastIndex)
                                    val sx = padL + cw * nextHover / (data.size - 1)
                                    if (kotlin.math.abs(sx - pos.x) < hitRadiusPx) {
                                        if (hoverIdx != nextHover) hoverIdx = nextHover
                                    } else {
                                        if (hoverIdx != -1) hoverIdx = -1
                                    }
                                }
                            }
                            if (ev.type == PointerEventType.Exit ||
                                (ev.type == PointerEventType.Release && ev.changes.all { !it.pressed }))
                                hoverIdx = -1
                        }
                    }
                }
        ) {
            val W = size.width; val H = size.height
            val cw = W - padL - padR; val ch = H - padT - padB
            for (i in 0..3) {
                val y = padT + ch * i / 3
                drawLine(color = gridColor, start = Offset(padL, y), end = Offset(W - padR, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(
                    "¥%.0f".format(maxVal - range * i / 3),
                    2f, y + 4f, chartPaint(0xFF9C8F80, 18f))
            }
            val pts = data.mapIndexed { idx, pt ->
                Offset(padL + cw * idx / (data.size - 1),
                    padT + ch * (1 - ((pt.value - minVal) / range).toFloat()))
            }
            val fillPath = Path().apply {
                moveTo(pts[0].x, padT + ch); lineTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val mx = (pts[i].x + pts[i - 1].x) / 2
                    cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                }
                lineTo(pts.last().x, padT + ch); close()
            }
            drawPath(path = fillPath, color = fillColor)
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val mx = (pts[i].x + pts[i - 1].x) / 2
                    cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                }
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            val step = if (data.size > 7) maxOf(1, data.size / 5) else 1
            pts.forEachIndexed { i, pt ->
                drawCircle(color = dotBg, radius = 4f, center = pt)
                drawCircle(color = lineColor, radius = 2.5f, center = pt)
                if (i % step == 0)
                    drawContext.canvas.nativeCanvas.drawText(
                        data[i].label, pt.x - 12f, H - 2f,
                        chartPaint(0xFF9C8F80, 17f))
            }
            if (hoverIdx in pts.indices) {
                val pt = pts[hoverIdx]
                drawLine(color = crosshairColor, start = Offset(pt.x, padT), end = Offset(pt.x, padT + ch),
                    strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                drawCircle(color = lineColor.copy(alpha = 0.12f), radius = 9f, center = pt)
                drawCircle(color = dotBg, radius = 5f, center = pt)
                drawCircle(color = lineColor, radius = 3.5f, center = pt)
            }
        }
        if (hoverIdx in data.indices && boxW > 0) {
            val w = boxW.toFloat(); val h = boxH.toFloat()
            val cw = w - padL - padR
            val ch = h - padT - padB
            val px = boxPadPx + padL + cw * hoverIdx / (data.size - 1)
            val py = padT + ch * (1 - ((data[hoverIdx].value - minVal) / range).toFloat())
            val gap = 6f
            val ct = padT

            // 修复 4：tooltip 动态宽高（取实际渲染尺寸，回退硬编码）
            val tipW = if (tipSize.x > 0) tipSize.x.toFloat() else 100f
            val tipH = if (tipSize.y > 0) tipSize.y.toFloat() else 50f

            val spaceAbove = py - ct
            val showAbove  = spaceAbove >= tipH + gap
            val rawOy = if (showAbove) (py - tipH - gap).toInt()
                        else (py + gap).toInt()
            val oy = rawOy.coerceIn(0, (h - tipH).toInt())

            // 修复 5：边界钳位到父容器
            val rawOx = (px - tipW / 2f).toInt()
            val ox = rawOx.coerceIn(0, (w - tipW).toInt())

            Surface(color = tooltipBg, shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset { IntOffset(ox, oy) }
                    .widthIn(min = 80.dp)
                    // 修复 2：捕获 tooltip 实际尺寸
                    .onSizeChanged { tipSize = IntOffset(it.width, it.height) }) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(data[hoverIdx].label, color = Color(0xFFC4B5A5),
                         style = MaterialTheme.typography.bodySmall)
                    Text("¥${"%.2f".format(data[hoverIdx].value)}",
                         color = Color(0xFFF1F5F9), fontWeight = FontWeight.Bold,
                         style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}