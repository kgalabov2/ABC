package com.alphabetwriter.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.alphabetwriter.GLYPH_BOX
import com.alphabetwriter.Glyph
import com.alphabetwriter.GridStyle
import com.alphabetwriter.PenStroke
import com.alphabetwriter.Pt
import kotlinx.coroutines.delay
import kotlin.math.hypot

// Line widths, in glyph-box units (the box is 109 units high).
private const val DEMO_WIDTH = 6.5f
private const val INK_WIDTH = 6f
private const val THUMB_WIDTH = 8f
private const val MARKER_SIZE = 7f

/** Finger strokes smaller than this (fraction of the pad height) are ignored as taps. */
private const val MIN_STROKE_EXTENT = 0.03f

/** A stroke and the square (cell) it belongs to; combinations such as きゃ use two cells side by side. */
class PlacedStroke(val stroke: PenStroke, val cell: Int)

fun placeStrokes(glyphs: List<Glyph>): List<PlacedStroke> =
    glyphs.flatMapIndexed { cell, glyph -> glyph.strokes.map { PlacedStroke(it, cell) } }

@Immutable
data class StrokeColors(
    val paper: Color,
    val border: Color,
    val grid: Color,
    val guide: Color,
    val ink: Color,
    /** The stroke being drawn or highlighted. */
    val active: Color,
    /** The square that marks where a stroke starts. */
    val start: Color,
    val tracing: Color,
)

@Composable
fun strokeColors(): StrokeColors {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    return remember(cs, dark) {
        StrokeColors(
            paper = cs.surfaceContainerLowest,
            border = cs.outlineVariant,
            grid = cs.outlineVariant,
            guide = cs.onSurface.copy(alpha = 0.08f),
            ink = cs.onSurface,
            active = if (dark) Color(0xFFFF6B6B) else Color(0xFFD62828),
            start = if (dark) Color(0xFF9CCC65) else Color(0xFF7CB342),
            tracing = cs.primary.copy(alpha = 0.22f),
        )
    }
}

/** State of the stroke-order animation: which strokes are drawn and how far the current one is. */
@Stable
class DemoState(val total: Int) {
    /** Number of strokes fully drawn. */
    var done by mutableIntStateOf(0)
    /** Progress (0..1) of stroke number [done]. */
    var fraction by mutableFloatStateOf(0f)
    var playing by mutableStateOf(true)
    var speed by mutableFloatStateOf(1f)
    /** Bumped by [replay] so the animation restarts even if it was already playing. */
    var restarts by mutableIntStateOf(0)

    /** The stroke shown in the active colour, or -1. */
    val highlighted: Int
        get() = when {
            fraction > 0f -> done
            !playing -> done - 1
            else -> -1
        }

    /** 1-based number of the stroke being drawn or last drawn, for the "Stroke n of m" text. */
    val currentNumber: Int get() = (if (fraction > 0f || playing) done + 1 else done).coerceIn(1, total.coerceAtLeast(1))

    fun replay() {
        done = 0
        fraction = 0f
        playing = true
        restarts++
    }

    fun togglePlay() {
        if (!playing && done >= total) {
            done = 0
            fraction = 0f
        }
        playing = !playing
    }

    /** Pauses on step [k] (0-based): strokes 0..k drawn, k highlighted. */
    fun showStep(k: Int) {
        playing = false
        done = k + 1
        fraction = 0f
    }

    fun cycleSpeed() {
        speed = when (speed) {
            1f -> 0.5f
            0.5f -> 2f
            else -> 1f
        }
    }
}

/** A rounded paper square (or a row of squares) that keeps its aspect ratio inside [modifier]. */
@Composable
private fun PaperCanvas(
    cells: Int,
    modifier: Modifier,
    description: String,
    inner: Modifier = Modifier,
    onDraw: DrawScope.(unit: Float) -> Unit,
) {
    val colors = strokeColors()
    val shape = RoundedCornerShape(12.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .aspectRatio(cells.toFloat(), matchHeightConstraintsFirst = true)
                .clip(shape)
                .background(colors.paper)
                .border(1.dp, colors.border, shape)
                .then(inner)
                .semantics { contentDescription = description },
        ) { onDraw(size.height / GLYPH_BOX) }
    }
}

/** The animated "how to write it" view: each stroke grows in red from a green start square. Tap to pause. */
@Composable
fun StrokeAnimation(
    strokes: List<PlacedStroke>,
    cells: Int,
    grid: GridStyle,
    demo: DemoState,
    modifier: Modifier = Modifier,
) {
    val colors = strokeColors()
    val measurer = rememberTextMeasurer(cacheSize = 24)

    LaunchedEffect(demo, demo.playing, demo.speed, demo.restarts) {
        if (!demo.playing || strokes.isEmpty()) return@LaunchedEffect
        // Pause, a step tap or Replay cancel this effect only at the next recomposition, and frame callbacks
        // (and delays) can still resume before that. Every write therefore checks the run is still current.
        val run = demo.restarts
        val speed = demo.speed
        fun current() = demo.playing && demo.restarts == run && demo.speed == speed
        while (true) {
            if (demo.done >= strokes.size) {
                delay((1600 / speed).toLong())
                if (!current()) return@LaunchedEffect
                demo.done = 0
                demo.fraction = 0f
                delay((400 / speed).toLong())
            }
            val from = demo.fraction
            if (from == 0f) delay((350 / speed).toLong()) // let the start square show first
            if (!current()) return@LaunchedEffect
            val full = (400f + strokes[demo.done].stroke.length * 9f) / speed
            animate(
                initialValue = from,
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ((1f - from) * full).toInt().coerceAtLeast(16),
                    easing = if (from == 0f) FastOutSlowInEasing else LinearEasing,
                ),
            ) { value, _ -> if (current()) demo.fraction = value }
            if (!current()) return@LaunchedEffect
            demo.done += 1
            demo.fraction = 0f
            delay((250 / speed).toLong())
        }
    }

    PaperCanvas(
        cells, modifier,
        description = "Stroke order animation. Tap to pause or play.",
        inner = Modifier.clickable { demo.togglePlay() },
    ) { unit ->
        drawGrid(grid, cells, unit, colors.grid)
        strokes.forEach { drawPolyline(it.stroke.points, it.cell, unit, colors.guide, DEMO_WIDTH * unit) }
        val done = demo.done.coerceAtMost(strokes.size)
        val highlighted = demo.highlighted
        for (i in 0 until done) {
            val s = strokes[i]
            drawPolyline(s.stroke.points, s.cell, unit, if (i == highlighted) colors.active else colors.ink, DEMO_WIDTH * unit)
        }
        val inProgress = done < strokes.size && (demo.playing || demo.fraction > 0f)
        if (inProgress) {
            val s = strokes[done]
            drawStartMarker(s.stroke.points.first(), s.cell, unit, colors.start)
            if (demo.fraction > 0f) {
                drawPolyline(s.stroke.prefix(demo.fraction), s.cell, unit, colors.active, DEMO_WIDTH * unit)
            }
        }
        val numbered = if (inProgress) done + 1 else done
        for (i in 0 until numbered) {
            val s = strokes[i]
            drawStrokeNumber(measurer, i + 1, s.stroke.label, s.cell, unit, if (i == highlighted || i == done) colors.active else colors.ink)
        }
    }
}

/** Numbered thumbnails: step k shows strokes 1..k, with stroke k in red. Tapping one pauses the animation there. */
@Composable
fun StepStrip(
    strokes: List<PlacedStroke>,
    cells: Int,
    demo: DemoState?,
    modifier: Modifier = Modifier,
) {
    val colors = strokeColors()
    // A new letter starts the strip at its first step.
    val listState = remember(strokes) { LazyListState() }
    val highlighted = demo?.highlighted ?: -1
    LaunchedEffect(listState, highlighted) {
        // Follow the animation, but never fight a finger that is scrolling the strip.
        if (highlighted >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((highlighted - 1).coerceAtLeast(0))
        }
    }
    LazyRow(modifier, state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(strokes.size) { k ->
            val selected = demo != null && !demo.playing && highlighted == k
            val shape = RoundedCornerShape(6.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier
                        .size(width = 46.dp * cells, height = 46.dp)
                        .clip(shape)
                        .background(colors.paper)
                        .border(if (selected) 2.dp else 1.dp, if (selected) colors.active else colors.border, shape)
                        .then(if (demo != null) Modifier.clickable { demo.showStep(k) } else Modifier)
                        .semantics { contentDescription = "Step ${k + 1}" },
                ) {
                    val unit = size.height / GLYPH_BOX
                    strokes.forEachIndexed { i, s ->
                        if (i != k) {
                            drawPolyline(s.stroke.points, s.cell, unit, if (i < k) colors.ink else colors.guide, THUMB_WIDTH * unit)
                        }
                    }
                    val s = strokes[k]
                    drawStartMarker(s.stroke.points.first(), s.cell, unit, colors.start, size = 14f)
                    drawPolyline(s.stroke.points, s.cell, unit, colors.active, THUMB_WIDTH * unit)
                }
                Text("${k + 1}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Free-hand writing area. [ink] holds the finished strokes in units of the pad height.
 * [showGuide] draws the whole letter faintly behind; [showHint] animates the next stroke to write.
 */
@Composable
fun WritingPad(
    strokes: List<PlacedStroke>,
    cells: Int,
    grid: GridStyle,
    ink: SnapshotStateList<List<Offset>>,
    showGuide: Boolean,
    showHint: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = strokeColors()
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val current = remember { mutableStateListOf<Offset>() }
    val hintIndex = ink.size
    val hinting = showHint && hintIndex < strokes.size
    val hintProgress = if (hinting) {
        // Keyed on the stroke, so the hint for the next stroke always starts from its beginning.
        key(hintIndex) {
            val transition = rememberInfiniteTransition(label = "hint")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1.25f, // the extra quarter holds the finished stroke briefly
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                label = "hint",
            )
            progress.coerceAtMost(1f)
        }
    } else {
        0f
    }

    PaperCanvas(
        cells, modifier,
        description = "Writing pad. Draw the letter with your finger.",
        inner = Modifier.pointerInput(ink) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val scale = size.height.toFloat()
                current.clear()
                current += down.position / scale
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.historical.forEach { current += it.position / scale }
                        current += change.position / scale
                        change.consume()
                        if (!change.pressed) break
                    }
                } finally {
                    // A tap or a brush of the palm is not a stroke; the shortest kana mark is ~9% of the square.
                    if (extent(current) >= MIN_STROKE_EXTENT) ink += current.toList()
                    current.clear()
                }
            }
        },
    ) { unit ->
        drawGrid(grid, cells, unit, colors.grid)
        if (showGuide) {
            strokes.forEach { drawPolyline(it.stroke.points, it.cell, unit, colors.tracing, DEMO_WIDTH * unit) }
        }
        if (hinting) {
            val s = strokes[hintIndex]
            drawPolyline(s.stroke.points, s.cell, unit, colors.active.copy(alpha = 0.2f), DEMO_WIDTH * unit)
            drawPolyline(s.stroke.prefix(hintProgress), s.cell, unit, colors.active.copy(alpha = 0.55f), DEMO_WIDTH * unit)
            drawStartMarker(s.stroke.points.first(), s.cell, unit, colors.start)
            drawArrowHead(s.stroke, s.cell, unit, colors.active)
            drawStrokeNumber(measurer, hintIndex + 1, s.stroke.label, s.cell, unit, colors.active)
        }
        val scale = size.height
        ink.forEach { drawInk(it, scale, colors.ink, INK_WIDTH * unit) }
        drawInk(current, scale, colors.ink, INK_WIDTH * unit)
    }
}

/** Diagonal of the points' bounding box, in units of the pad height. */
private fun extent(points: List<Offset>): Float {
    if (points.isEmpty()) return 0f
    val w = points.maxOf { it.x } - points.minOf { it.x }
    val h = points.maxOf { it.y } - points.minOf { it.y }
    return hypot(w, h)
}

private fun DrawScope.drawGrid(style: GridStyle, cells: Int, unit: Float, color: Color) {
    val cellWidth = GLYPH_BOX * unit
    val thin = 1.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    when (style) {
        GridStyle.SQUARE -> for (cell in 0 until cells) {
            val x0 = cell * cellWidth
            drawLine(color, Offset(x0 + cellWidth / 2, 0f), Offset(x0 + cellWidth / 2, size.height), thin, pathEffect = dash)
            drawLine(color, Offset(x0, size.height / 2), Offset(x0 + cellWidth, size.height / 2), thin, pathEffect = dash)
            if (cell > 0) drawLine(color, Offset(x0, 0f), Offset(x0, size.height), thin)
        }
        // Capital/ascender line, x-height, baseline (solid), descender line.
        GridStyle.LINES -> for ((y, solid) in listOf(22f to false, 47f to false, 77f to true, 98f to false)) {
            drawLine(
                color, Offset(0f, y * unit), Offset(size.width, y * unit),
                if (solid) thin * 1.5f else thin, pathEffect = if (solid) null else dash,
            )
        }
    }
}

private fun DrawScope.drawPolyline(points: List<Pt>, cell: Int, unit: Float, color: Color, width: Float) {
    if (points.size < 2) return
    val ox = cell * GLYPH_BOX * unit
    val path = Path()
    path.moveTo(ox + points[0].x * unit, points[0].y * unit)
    for (i in 1 until points.size) path.lineTo(ox + points[i].x * unit, points[i].y * unit)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawStartMarker(p: Pt, cell: Int, unit: Float, color: Color, size: Float = MARKER_SIZE) {
    val s = size * unit
    val center = Offset(cell * GLYPH_BOX * unit + p.x * unit, p.y * unit)
    drawRoundRect(color, topLeft = center - Offset(s / 2, s / 2), size = Size(s, s), cornerRadius = CornerRadius(s * 0.2f))
}

/** A small chevron just past the end of [stroke], pointing the way the pen moves. */
private fun DrawScope.drawArrowHead(stroke: PenStroke, cell: Int, unit: Float, color: Color) {
    val pts = stroke.points
    val end = pts.last()
    var i = pts.size - 2
    while (i > 0 && hypot(end.x - pts[i].x, end.y - pts[i].y) < 5f) i--
    val dx = end.x - pts[i].x
    val dy = end.y - pts[i].y
    val len = hypot(dx, dy)
    if (len == 0f) return
    val ux = dx / len
    val uy = dy / len
    val ox = cell * GLYPH_BOX * unit
    val tip = Offset(ox + (end.x + ux * 7f) * unit, (end.y + uy * 7f) * unit)
    val back = Offset(-ux * 6f * unit, -uy * 6f * unit)
    val side = Offset(-uy * 4.5f * unit, ux * 4.5f * unit)
    val path = Path().apply {
        moveTo(tip.x + back.x + side.x, tip.y + back.y + side.y)
        lineTo(tip.x, tip.y)
        lineTo(tip.x + back.x - side.x, tip.y + back.y - side.y)
    }
    drawPath(path, color, style = Stroke(width = 2.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawStrokeNumber(
    measurer: TextMeasurer, n: Int, label: Pt, cell: Int, unit: Float, color: Color,
) {
    val layout = measurer.measure(
        AnnotatedString(n.toString()),
        style = TextStyle(fontSize = (8f * unit).toSp(), fontWeight = FontWeight.Bold, color = color),
    )
    drawText(layout, topLeft = Offset(cell * GLYPH_BOX * unit + label.x * unit, label.y * unit - layout.firstBaseline))
}

/** Smooths finger input with quadratic curves through the midpoints of successive samples. */
private fun DrawScope.drawInk(points: List<Offset>, scale: Float, color: Color, width: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color, width / 2, points[0] * scale)
        return
    }
    val path = Path()
    val first = points[0] * scale
    path.moveTo(first.x, first.y)
    for (i in 1 until points.size - 1) {
        val a = points[i] * scale
        val b = points[i + 1] * scale
        path.quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
    }
    val last = points.last() * scale
    path.lineTo(last.x, last.y)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
