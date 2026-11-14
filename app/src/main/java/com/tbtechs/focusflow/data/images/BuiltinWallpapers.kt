package com.tbtechs.focusflow.data.images

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Wallpapers drawn by the app itself, so there are no image files to license or download.
 *
 * Every wallpaper is drawn from relative coordinates with a fixed random seed, so the small
 * preview, the launcher backdrop and the full-size bitmap used as the phone wallpaper all
 * show the same picture.
 */
object BuiltinWallpapers {

    enum class Style { MESH, RIBBONS, DUNES, RIDGES, ORBS, WAVES, STARS, RIPPLE }

    /** [bestAt] is the Glassiness (0-100) at which the launcher's frosted panels read best on it. */
    class Spec(
        val id: String,
        val name: String,
        val bestAt: Int,
        val style: Style,
        val colors: IntArray,
        val seed: Long,
    )

    val all: List<Spec> = listOf(
        Spec("midnight", "Midnight", 40, Style.MESH, intArrayOf(0xFF4F46E5.toInt(), 0xFF1E3A8A.toInt(), 0xFF7C3AED.toInt()), 11),
        Spec("aurora", "Aurora", 60, Style.RIBBONS, intArrayOf(0xFF10B981.toInt(), 0xFF6366F1.toInt(), 0xFF06B6D4.toInt()), 12),
        Spec("dusk", "Dusk", 55, Style.DUNES, intArrayOf(0xFFF97316.toInt(), 0xFFDB2777.toInt(), 0xFF7C3AED.toInt()), 13),
        Spec("forest", "Forest", 50, Style.RIDGES, intArrayOf(0xFF059669.toInt(), 0xFF0F766E.toInt(), 0xFF65A30D.toInt()), 14),
        Spec("ocean", "Ocean", 65, Style.WAVES, intArrayOf(0xFF0EA5E9.toInt(), 0xFF2563EB.toInt(), 0xFF14B8A6.toInt()), 15),
        Spec("graphite", "Graphite", 30, Style.RIPPLE, intArrayOf(0xFF64748B.toInt(), 0xFF334155.toInt(), 0xFF94A3B8.toInt()), 16),
        Spec("nebula", "Nebula", 45, Style.STARS, intArrayOf(0xFF8B5CF6.toInt(), 0xFF2563EB.toInt(), 0xFFEC4899.toInt()), 17),
        Spec("neon", "Neon", 60, Style.ORBS, intArrayOf(0xFFF43F5E.toInt(), 0xFF22D3EE.toInt(), 0xFFA3E635.toInt()), 18),
        Spec("ember", "Ember", 50, Style.MESH, intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF9A3412.toInt()), 19),
        Spec("glacier", "Glacier", 55, Style.RIDGES, intArrayOf(0xFF38BDF8.toInt(), 0xFF6366F1.toInt(), 0xFFE0F2FE.toInt()), 20),
        Spec("lagoon", "Lagoon", 60, Style.WAVES, intArrayOf(0xFF14B8A6.toInt(), 0xFF0EA5E9.toInt(), 0xFF34D399.toInt()), 21),
        Spec("sakura", "Sakura", 50, Style.DUNES, intArrayOf(0xFFF472B6.toInt(), 0xFFA855F7.toInt(), 0xFFFB7185.toInt()), 22),
    )

    fun byId(id: String?): Spec? = all.firstOrNull { it.id == id }

    private const val DEEP = 0xFF05060D.toInt()

    fun render(spec: Spec, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rnd = Random(spec.seed)
        when (spec.style) {
            Style.MESH -> mesh(canvas, w, h, spec.colors, rnd)
            Style.RIBBONS -> ribbons(canvas, w, h, spec.colors, rnd)
            Style.DUNES -> dunes(canvas, w, h, spec.colors, rnd)
            Style.RIDGES -> ridges(canvas, w, h, spec.colors, rnd)
            Style.ORBS -> orbs(canvas, w, h, spec.colors, rnd)
            Style.WAVES -> waves(canvas, w, h, spec.colors, rnd)
            Style.STARS -> stars(canvas, w, h, spec.colors, rnd)
            Style.RIPPLE -> ripple(canvas, w, h, spec.colors, rnd)
        }
        grain(canvas, w, h, rnd)
        return bitmap
    }

    /** Soft glows built from three colours, such as the phone's own wallpaper colours. */
    fun adaptive(palette: IntArray, w: Int, h: Int): Bitmap =
        render(Spec("adaptive", "Adaptive", 70, Style.MESH, palette, 7), w, h)

    /** Three colours from the phone's own wallpaper, for the Adaptive look. */
    fun phonePalette(context: Context): IntArray {
        val fallback = intArrayOf(0xFF4F46E5.toInt(), 0xFF0EA5E9.toInt(), 0xFFC026D3.toInt())
        return try {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return fallback
            intArrayOf(
                colors.primaryColor.toArgb(),
                colors.secondaryColor?.toArgb() ?: fallback[1],
                colors.tertiaryColor?.toArgb() ?: fallback[2],
            )
        } catch (_: Exception) {
            fallback
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = 1f - t
        return Color.argb(
            255,
            (Color.red(a) * k + Color.red(b) * t).toInt(),
            (Color.green(a) * k + Color.green(b) * t).toInt(),
            (Color.blue(a) * k + Color.blue(b) * t).toInt(),
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)

    private fun verticalGradient(canvas: Canvas, w: Int, h: Int, colors: IntArray, stops: FloatArray? = null) {
        val p = paint()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun glow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float, screen: Boolean = false) {
        val p = paint()
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha * 0.35f), withAlpha(color, 0f)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        if (screen) p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        canvas.drawCircle(cx, cy, r, p)
    }

    /** Fine film grain, which also hides banding in the smooth gradients. */
    private fun grain(canvas: Canvas, w: Int, h: Int, rnd: Random) {
        val p = Paint().apply { strokeWidth = max(1f, w / 540f) }
        val count = (w.toLong() * h / 700L).toInt().coerceIn(500, 6000)
        repeat(count) {
            val light = rnd.nextBoolean()
            p.color = if (light) Color.argb(10 + rnd.nextInt(10), 255, 255, 255)
            else Color.argb(10 + rnd.nextInt(14), 0, 0, 0)
            canvas.drawPoint(rnd.nextFloat() * w, rnd.nextFloat() * h, p)
        }
    }

    // ── styles ─────────────────────────────────────────────────────────────────

    private fun mesh(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(c, w, h, intArrayOf(mix(p[0], DEEP, 0.62f), mix(p[1], DEEP, 0.78f), DEEP), floatArrayOf(0f, 0.55f, 1f))
        for (i in 0 until 5) {
            val x = (0.08f + 0.84f * rnd.nextFloat()) * w
            val y = ((i / 4f) + (rnd.nextFloat() - 0.5f) * 0.16f).coerceIn(0.02f, 0.98f) * h
            val r = w * (0.55f + 0.5f * rnd.nextFloat())
            val base = p[i % 3]
            glow(c, x, y, r, if (i % 2 == 0) mix(base, Color.WHITE, 0.12f) else base, 0.38f + 0.22f * rnd.nextFloat())
        }
    }

    private fun ribbons(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(c, w, h, intArrayOf(DEEP, mix(p[1], DEEP, 0.82f), mix(p[0], DEEP, 0.9f)), floatArrayOf(0f, 0.6f, 1f))
        for (i in 0 until 4) {
            val baseY = h * (0.18f + 0.14f * i + 0.04f * rnd.nextFloat())
            val amp = h * (0.03f + 0.04f * rnd.nextFloat())
            val freq = (1.2f + rnd.nextFloat() * 1.6f) * (2f * PI.toFloat()) / w
            val phase = rnd.nextFloat() * 6.28f
            val length = h * (0.22f + 0.12f * rnd.nextFloat())
            val color = p[i % 3]
            val path = Path()
            val step = max(4, w / 90)
            var x = 0
            path.moveTo(0f, baseY)
            while (x <= w) {
                path.lineTo(x.toFloat(), baseY + amp * sin(x * freq + phase))
                x += step
            }
            x = w
            while (x >= 0) {
                path.lineTo(x.toFloat(), baseY + length + amp * 1.4f * sin(x * freq * 0.7f + phase + 1.3f))
                x -= step
            }
            path.close()
            val paint = paint()
            paint.shader = LinearGradient(
                0f, baseY - amp, 0f, baseY + length + amp,
                intArrayOf(withAlpha(mix(color, Color.WHITE, 0.25f), 0.75f), withAlpha(color, 0.30f), withAlpha(color, 0f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            c.drawPath(path, paint)
        }
        stars(c, w, h, p, rnd, count = w * h / 26000, bright = 0)
    }

    private fun dunes(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(
            c, w, h,
            intArrayOf(mix(p[2], DEEP, 0.7f), mix(p[1], DEEP, 0.25f), mix(p[0], Color.WHITE, 0.12f)),
            floatArrayOf(0f, 0.5f, 0.66f),
        )
        val sunX = w * (0.35f + 0.3f * rnd.nextFloat())
        val sunY = h * 0.6f
        glow(c, sunX, sunY, w * 0.85f, mix(p[0], Color.WHITE, 0.35f), 0.85f)
        val disc = paint()
        disc.color = mix(Color.WHITE, p[0], 0.18f)
        c.drawCircle(sunX, sunY, w * 0.085f, disc)
        val layers = 3
        for (i in 0 until layers) {
            val base = h * (0.64f + 0.105f * i)
            val amp = h * (0.035f - 0.006f * i)
            val f1 = (1.1f + 0.7f * rnd.nextFloat()) * 2f * PI.toFloat() / w
            val f2 = f1 * 2.3f
            val ph1 = rnd.nextFloat() * 6.28f
            val ph2 = rnd.nextFloat() * 6.28f
            val path = Path()
            path.moveTo(0f, h.toFloat())
            var x = 0
            val step = max(4, w / 120)
            while (x <= w) {
                val y = base + amp * sin(x * f1 + ph1) + amp * 0.35f * sin(x * f2 + ph2)
                path.lineTo(x.toFloat(), y)
                x += step
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            val paint = paint()
            val top = mix(p[2], DEEP, 0.45f + 0.18f * i)
            paint.shader = LinearGradient(
                0f, base - amp, 0f, h.toFloat(),
                intArrayOf(mix(top, p[0], 0.22f - 0.06f * i), mix(top, DEEP, 0.55f)),
                null,
                Shader.TileMode.CLAMP,
            )
            c.drawPath(path, paint)
        }
    }

    private fun ridges(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(
            c, w, h,
            intArrayOf(mix(p[1], DEEP, 0.78f), mix(p[0], DEEP, 0.42f), mix(p[2], Color.WHITE, 0.08f)),
            floatArrayOf(0f, 0.55f, 0.72f),
        )
        stars(c, w, h, p, rnd, count = w * h / 40000, bright = 0)
        glow(c, w * 0.78f, h * 0.16f, w * 0.28f, Color.WHITE, 0.55f)
        val moon = paint()
        moon.color = withAlpha(Color.WHITE, 0.9f)
        c.drawCircle(w * 0.78f, h * 0.16f, w * 0.035f, moon)
        val layers = 4
        for (i in 0 until layers) {
            val base = h * (0.52f + 0.1f * i)
            val amp = h * (0.075f - 0.01f * i)
            val phases = FloatArray(4) { rnd.nextFloat() * 6.28f }
            val path = Path()
            path.moveTo(0f, h.toFloat())
            val step = max(6, w / 28)
            var x = 0
            while (x <= w + step) {
                val t = x.toFloat() / w
                val y = base - amp * (
                    0.55f * sin(t * 2f * PI.toFloat() * 1.3f + phases[0]) +
                        0.30f * sin(t * 2f * PI.toFloat() * 3.1f + phases[1]) +
                        0.18f * sin(t * 2f * PI.toFloat() * 6.7f + phases[2])
                    ) - amp * 0.22f * rnd.nextFloat()
                path.lineTo(x.toFloat(), y)
                x += step
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            val paint = paint()
            val tone = mix(p[1], DEEP, 0.35f + 0.2f * i)
            paint.shader = LinearGradient(
                0f, base - amp, 0f, base + h * 0.2f,
                intArrayOf(mix(tone, p[2], 0.16f), mix(tone, DEEP, 0.35f)),
                null,
                Shader.TileMode.CLAMP,
            )
            c.drawPath(path, paint)
            // Mist between the ridges.
            val mist = paint()
            mist.shader = LinearGradient(
                0f, base - amp * 0.2f, 0f, base + h * 0.09f,
                intArrayOf(withAlpha(Color.WHITE, 0f), withAlpha(mix(p[0], Color.WHITE, 0.5f), 0.16f), withAlpha(Color.WHITE, 0f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, base - amp * 0.2f, w.toFloat(), base + h * 0.09f, mist)
        }
    }

    private fun orbs(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(c, w, h, intArrayOf(mix(p[1], DEEP, 0.9f), DEEP, mix(p[0], DEEP, 0.92f)))
        for (i in 0 until 7) {
            val x = (0.05f + 0.9f * rnd.nextFloat()) * w
            val y = (0.05f + 0.9f * rnd.nextFloat()) * h
            val r = w * (0.14f + 0.34f * rnd.nextFloat())
            glow(c, x, y, r, p[i % 3], 0.55f, screen = true)
            if (i < 3) {
                val ring = paint()
                ring.style = Paint.Style.STROKE
                ring.strokeWidth = max(1.5f, w / 360f)
                ring.color = withAlpha(p[i % 3], 0.28f)
                c.drawCircle(x, y, r * 0.62f, ring)
            }
        }
    }

    private fun waves(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(c, w, h, intArrayOf(mix(p[1], DEEP, 0.55f), mix(p[0], DEEP, 0.7f), DEEP), floatArrayOf(0f, 0.5f, 1f))
        glow(c, w * 0.75f, h * 0.14f, w * 0.7f, mix(p[2], Color.WHITE, 0.3f), 0.42f)
        val layers = 6
        for (i in 0 until layers) {
            val base = h * (0.34f + 0.115f * i)
            val amp = h * (0.016f + 0.006f * rnd.nextFloat())
            val wavelength = w * (0.75f + 0.7f * rnd.nextFloat())
            val phase = rnd.nextFloat() * 6.28f
            val color = p[i % 3]
            val path = Path()
            path.moveTo(0f, h.toFloat())
            val step = max(4, w / 120)
            var x = 0
            while (x <= w) {
                path.lineTo(x.toFloat(), base + amp * sin(x * 2f * PI.toFloat() / wavelength + phase))
                x += step
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            val fill = paint()
            fill.shader = LinearGradient(
                0f, base - amp, 0f, base + h * 0.2f,
                intArrayOf(withAlpha(mix(color, Color.WHITE, 0.15f), 0.42f), withAlpha(color, 0.10f)),
                null,
                Shader.TileMode.CLAMP,
            )
            c.drawPath(path, fill)
            val edge = paint()
            edge.style = Paint.Style.STROKE
            edge.strokeWidth = max(1.5f, w / 300f)
            edge.color = withAlpha(mix(color, Color.WHITE, 0.55f), 0.38f)
            val line = Path()
            var lx = 0
            line.moveTo(0f, base + amp * sin(phase))
            while (lx <= w) {
                line.lineTo(lx.toFloat(), base + amp * sin(lx * 2f * PI.toFloat() / wavelength + phase))
                lx += step
            }
            c.drawPath(line, edge)
        }
    }

    private fun stars(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random, count: Int = w * h / 9000, bright: Int = 10) {
        if (bright > 0) {
            verticalGradient(c, w, h, intArrayOf(DEEP, mix(p[1], DEEP, 0.8f), mix(p[0], DEEP, 0.9f)))
            glow(c, w * 0.25f, h * 0.3f, w * 0.9f, p[0], 0.34f)
            glow(c, w * 0.85f, h * 0.55f, w * 0.85f, p[2], 0.30f)
            glow(c, w * 0.4f, h * 0.88f, w * 0.8f, p[1], 0.30f)
        }
        val dot = paint()
        repeat(count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = (0.5f + rnd.nextFloat() * 1.3f) * (w / 540f)
            dot.color = Color.argb(70 + rnd.nextInt(170), 255, 255, 255)
            c.drawCircle(x, y, r, dot)
        }
        repeat(bright) {
            val x = (0.08f + 0.84f * rnd.nextFloat()) * w
            val y = (0.05f + 0.9f * rnd.nextFloat()) * h
            glow(c, x, y, w * 0.035f, Color.WHITE, 0.9f)
            val flare = paint()
            flare.strokeWidth = max(1f, w / 540f)
            flare.color = withAlpha(Color.WHITE, 0.55f)
            c.drawLine(x - w * 0.02f, y, x + w * 0.02f, y, flare)
            c.drawLine(x, y - w * 0.02f, x, y + w * 0.02f, flare)
        }
    }

    private fun ripple(c: Canvas, w: Int, h: Int, p: IntArray, rnd: Random) {
        verticalGradient(c, w, h, intArrayOf(mix(p[1], DEEP, 0.5f), mix(p[1], DEEP, 0.78f), DEEP), floatArrayOf(0f, 0.5f, 1f))
        val cx = w * (0.4f + 0.2f * rnd.nextFloat())
        val cy = h * 0.36f
        glow(c, cx, cy, w * 0.75f, mix(p[2], Color.WHITE, 0.2f), 0.30f)
        val ring = paint()
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = max(1.5f, w / 280f)
        var r = w * 0.1f
        var i = 0
        while (r < h * 1.3f) {
            ring.color = withAlpha(mix(p[2], Color.WHITE, 0.35f), (0.30f - 0.012f * i).coerceAtLeast(0.025f))
            c.drawCircle(cx, cy, r, ring)
            r += w * (0.105f + 0.004f * i)
            i++
        }
    }
}
