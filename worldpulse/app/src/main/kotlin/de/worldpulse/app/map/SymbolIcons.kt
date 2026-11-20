package de.worldpulse.app.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import kotlin.math.cos
import kotlin.math.sin

/**
 * Eigene, neutrale Piktogramme (SPEC 3.2) zur Laufzeit gezeichnet – keine fremden Icon-Sets.
 * Ring: durchgezogen = bestätigt, gestrichelt = gemeldet/unbestätigt, halb = umstritten.
 */
object SymbolIcons {
    fun name(s: Symbol, r: StatusRing) = "sym_${s.name}_${r.name}"

    fun color(s: Symbol): Int = when (s) {
        Symbol.KAMPFHANDLUNG, Symbol.EXPLOSION_BESCHUSS, Symbol.GEWALT_ZIVILISTEN -> 0xFFE5534B.toInt()
        Symbol.PROTEST -> 0xFFF0883E.toInt()
        Symbol.ERDBEBEN -> 0xFFC9A227.toInt()
        Symbol.BRAND -> 0xFFFF6B3D.toInt()
        Symbol.UNWETTER_KATASTROPHE -> 0xFF4FA3E0.toInt()
        Symbol.POLITIK_WAHL -> 0xFFA371F7.toInt()
        Symbol.EVENT -> 0xFF3FB950.toInt()
        Symbol.SONSTIGES -> 0xFF8B98A5.toInt()
    }

    fun all(sizePx: Int): Map<String, Bitmap> =
        Symbol.entries.flatMap { s -> StatusRing.entries.map { r -> name(s, r) to draw(s, r, sizePx) } }.toMap()

    fun draw(s: Symbol, r: StatusRing, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f; val cy = size / 2f
        val ringW = size * 0.09f
        val rad = size / 2f - ringW
        val col = color(s)

        // Hintergrundscheibe
        c.drawCircle(cx, cy, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60D141B.toInt() })

        // Status-Ring
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringW; color = col }
        val oval = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        when (r) {
            StatusRing.BESTAETIGT -> c.drawCircle(cx, cy, rad, ring)
            StatusRing.GEMELDET -> c.drawCircle(cx, cy, rad, Paint(ring).apply { pathEffect = DashPathEffect(floatArrayOf(size * 0.12f, size * 0.09f), 0f) })
            StatusRing.UMSTRITTEN -> {
                c.drawArc(oval, 90f, 180f, false, ring)
                c.drawArc(oval, 270f, 180f, false, Paint(ring).apply { pathEffect = DashPathEffect(floatArrayOf(size * 0.12f, size * 0.09f), 0f) })
            }
        }

        // Piktogramm
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.FILL }
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = size * 0.08f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val u = rad * 0.55f // Einheit für das innere Zeichen
        when (s) {
            Symbol.KAMPFHANDLUNG -> { // gekreuzte Linien
                c.drawLine(cx - u, cy - u, cx + u, cy + u, st); c.drawLine(cx + u, cy - u, cx - u, cy + u, st)
            }
            Symbol.EXPLOSION_BESCHUSS -> c.drawPath(star(cx, cy, u * 1.1f, u * 0.45f, 8), p)
            Symbol.GEWALT_ZIVILISTEN -> { // Warndreieck mit Ausrufezeichen
                val t = Path().apply { moveTo(cx, cy - u); lineTo(cx + u, cy + u * 0.8f); lineTo(cx - u, cy + u * 0.8f); close() }
                c.drawPath(t, Paint(st).apply { strokeWidth = size * 0.06f })
                c.drawLine(cx, cy - u * 0.35f, cx, cy + u * 0.2f, Paint(st).apply { strokeWidth = size * 0.06f })
                c.drawCircle(cx, cy + u * 0.52f, size * 0.03f, p)
            }
            Symbol.PROTEST -> { // Fahne
                c.drawLine(cx - u * 0.7f, cy - u, cx - u * 0.7f, cy + u, st)
                c.drawPath(Path().apply { moveTo(cx - u * 0.7f, cy - u); lineTo(cx + u, cy - u * 0.55f); lineTo(cx - u * 0.7f, cy - u * 0.05f); close() }, p)
            }
            Symbol.ERDBEBEN -> { // Zickzack
                val z = Path().apply {
                    moveTo(cx - u, cy); lineTo(cx - u * 0.6f, cy); lineTo(cx - u * 0.3f, cy - u * 0.8f)
                    lineTo(cx + u * 0.05f, cy + u * 0.8f); lineTo(cx + u * 0.35f, cy - u * 0.4f); lineTo(cx + u * 0.6f, cy); lineTo(cx + u, cy)
                }
                c.drawPath(z, st)
            }
            Symbol.BRAND -> { // Flamme
                val f = Path().apply {
                    moveTo(cx, cy - u * 1.05f)
                    cubicTo(cx + u * 0.9f, cy - u * 0.2f, cx + u * 0.8f, cy + u, cx, cy + u)
                    cubicTo(cx - u * 0.8f, cy + u, cx - u * 0.9f, cy - u * 0.1f, cx - u * 0.2f, cy - u * 0.4f)
                    cubicTo(cx - u * 0.1f, cy, cx + u * 0.1f, cy - u * 0.5f, cx, cy - u * 1.05f); close()
                }
                c.drawPath(f, p)
            }
            Symbol.UNWETTER_KATASTROPHE -> { // Blitz
                val b = Path().apply {
                    moveTo(cx + u * 0.2f, cy - u); lineTo(cx - u * 0.55f, cy + u * 0.1f); lineTo(cx - u * 0.05f, cy + u * 0.1f)
                    lineTo(cx - u * 0.25f, cy + u); lineTo(cx + u * 0.55f, cy - u * 0.15f); lineTo(cx + u * 0.05f, cy - u * 0.15f); close()
                }
                c.drawPath(b, p)
            }
            Symbol.POLITIK_WAHL -> { // Urne mit Schlitz
                c.drawRect(cx - u * 0.8f, cy - u * 0.2f, cx + u * 0.8f, cy + u * 0.85f, Paint(st).apply { strokeWidth = size * 0.06f })
                c.drawLine(cx - u * 0.35f, cy - u * 0.2f, cx + u * 0.35f, cy - u * 0.2f, Paint(st).apply { color = 0xFF0D141B.toInt(); strokeWidth = size * 0.07f })
                c.drawRect(cx - u * 0.3f, cy - u * 0.95f, cx + u * 0.3f, cy - u * 0.05f, p)
            }
            Symbol.EVENT -> c.drawPath(star(cx, cy, u * 1.05f, u * 0.45f, 5), p)
            Symbol.SONSTIGES -> c.drawCircle(cx, cy, u * 0.45f, p)
        }
        return bmp
    }

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path {
        val path = Path()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.PI * i / points - Math.PI / 2
            val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); return path
    }
}
