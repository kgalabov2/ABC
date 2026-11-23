package com.alphabetwriter

import kotlin.math.ceil
import kotlin.math.hypot

/** Size of KanjiVG's square drawing box. All stroke coordinates are in these units. */
const val GLYPH_BOX = 109f

/** A point in KanjiVG's 109 x 109 box. */
data class Pt(val x: Float, val y: Float)

/** One pen stroke flattened to a polyline, plus where KanjiVG puts its stroke-number label. */
class PenStroke(val points: List<Pt>, val label: Pt) {
    /** cumulative[i] = polyline length from points[0] to points[i]. */
    private val cumulative = FloatArray(points.size)

    init {
        require(points.size >= 2) { "a stroke needs at least two points" }
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] +
                hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
    }

    val length: Float get() = cumulative.last()

    /** The points of the first [fraction] (0..1) of the stroke, ending exactly at that fraction. */
    fun prefix(fraction: Float): List<Pt> {
        if (fraction >= 1f) return points
        val target = fraction.coerceAtLeast(0f) * length
        val out = ArrayList<Pt>()
        out += points[0]
        for (i in 1 until points.size) {
            if (cumulative[i] < target) {
                out += points[i]
                continue
            }
            out += pointBetween(i, target)
            break
        }
        return out
    }

    /** The point reached after the first [fraction] (0..1) of the stroke. */
    fun pointAt(fraction: Float): Pt = prefix(fraction).last()

    private fun pointBetween(i: Int, target: Float): Pt {
        val segment = cumulative[i] - cumulative[i - 1]
        if (segment <= 0f) return points[i]
        val t = ((target - cumulative[i - 1]) / segment).coerceIn(0f, 1f)
        val a = points[i - 1]
        val b = points[i]
        return Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
}

/** The strokes of one kana character, in writing order. */
class Glyph(val char: Char, val strokes: List<PenStroke>)

/** Reads the `kana_strokes.txt` asset (see its header for the format). */
object StrokeData {
    fun parse(lines: Sequence<String>): Map<Char, Glyph> {
        val glyphs = LinkedHashMap<Char, Glyph>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val fields = line.split('\t')
            require(fields.size >= 2 && fields[0].length == 1) { "bad line: ${line.take(40)}" }
            val strokes = fields.drop(1).map { field ->
                val at = field.lastIndexOf('@')
                require(at > 0) { "stroke without a label position: ${field.take(40)}" }
                val (lx, ly) = field.substring(at + 1).split(',').map { it.toFloat() }
                PenStroke(SvgPath.flatten(field.substring(0, at)), Pt(lx, ly))
            }
            glyphs[fields[0][0]] = Glyph(fields[0][0], strokes)
        }
        return glyphs
    }
}

/** Turns SVG path data into a polyline. Supports M L H V C S Q Z in both absolute and relative form. */
object SvgPath {
    private val token = Regex("[MmLlHhVvCcSsQqZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

    fun flatten(d: String): List<Pt> {
        val tokens = token.findAll(d).map { it.value }.toList()
        val out = ArrayList<Pt>()
        var i = 0
        var cmd = ' '
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        // Second control point of the previous cubic, for S.
        var lastCtrlX = 0f
        var lastCtrlY = 0f
        var lastWasCubic = false

        fun isNumber() = i < tokens.size && !tokens[i][0].isLetter()
        fun num(): Float {
            require(i < tokens.size && !tokens[i][0].isLetter()) { "path data ends early: $d" }
            return tokens[i++].toFloat()
        }

        while (i < tokens.size) {
            if (isNumber()) {
                require(cmd != ' ') { "path data must start with a command: $d" }
            } else {
                cmd = tokens[i++][0]
            }
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            var cubic = false
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + num(); y = oy + num()
                    startX = x; startY = y
                    out += Pt(x, y)
                    cmd = if (rel) 'l' else 'L' // further coordinate pairs are line-tos
                }
                'L' -> { x = ox + num(); y = oy + num(); out += Pt(x, y) }
                'H' -> { x = ox + num(); out += Pt(x, y) }
                'V' -> { y = oy + num(); out += Pt(x, y) }
                'C', 'S' -> {
                    val c1x: Float
                    val c1y: Float
                    if (cmd.uppercaseChar() == 'C') {
                        c1x = ox + num(); c1y = oy + num()
                    } else if (lastWasCubic) {
                        c1x = 2 * x - lastCtrlX; c1y = 2 * y - lastCtrlY
                    } else {
                        c1x = x; c1y = y
                    }
                    val c2x = ox + num()
                    val c2y = oy + num()
                    val ex = ox + num()
                    val ey = oy + num()
                    cubicTo(out, x, y, c1x, c1y, c2x, c2y, ex, ey)
                    lastCtrlX = c2x; lastCtrlY = c2y
                    x = ex; y = ey
                    cubic = true
                }
                'Q' -> {
                    val cx = ox + num()
                    val cy = oy + num()
                    val ex = ox + num()
                    val ey = oy + num()
                    // A quadratic is a cubic with both control points 2/3 of the way to the quadratic one.
                    cubicTo(
                        out, x, y,
                        x + 2f / 3f * (cx - x), y + 2f / 3f * (cy - y),
                        ex + 2f / 3f * (cx - ex), ey + 2f / 3f * (cy - ey),
                        ex, ey,
                    )
                    x = ex; y = ey
                }
                'Z' -> {
                    x = startX; y = startY
                    out += Pt(x, y)
                    cmd = ' ' // Z takes no numbers
                }
                else -> throw IllegalArgumentException("unsupported path command '$cmd' in $d")
            }
            lastWasCubic = cubic
        }
        require(out.size >= 2) { "path has fewer than two points: $d" }
        return out
    }

    private fun cubicTo(
        out: MutableList<Pt>,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
    ) {
        // Enough segments that each is about one unit long (a 109-unit glyph drawn ~300 dp wide).
        val hull = hypot(x1 - x0, y1 - y0) + hypot(x2 - x1, y2 - y1) + hypot(x3 - x2, y3 - y2)
        val steps = ceil(hull).toInt().coerceIn(2, 48)
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val u = 1 - t
            val a = u * u * u
            val b = 3 * u * u * t
            val c = 3 * u * t * t
            val e = t * t * t
            out += Pt(a * x0 + b * x1 + c * x2 + e * x3, a * y0 + b * y1 + c * y2 + e * y3)
        }
    }
}
