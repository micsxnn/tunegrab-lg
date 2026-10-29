package com.tunegrab.app.lg

import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Squircle iOS (Liquid Glass, tunegrab-lg v0.23.0): o canto da Apple não é
 * um arco de círculo — é uma SUPERELIPSE (curva contínua que transita de
 * reto pra curvo sem o "ponto de tangência" que denuncia canto Android).
 *
 * Fórmula: |x/a|^n + |y/b|^n = 1 desenhada por segmentos no quadrante de
 * cada canto, com n controlando a "maciez" (iOS usa ~5 em elementos de UI).
 * Cada canto é a curva completa do quadrante ancorada a `radius` das bordas
 * — a transição reto/curvo fica contínua, igual ao "corner smoothing 100%"
 * do Figma.
 *
 * Uso:
 *   view.outlineProvider = Squircle.outline(radiusPx)  // clip + sombra
 *   view.clipToOutline = true                          // recorta conteúdo
 *   background = GlassPaintDrawable(radius)            // fundo casando
 */
object Squircle {

    private const val N = 5.0   // expoente da superelipse (feel iOS)
    private const val STEPS = 28

    /** Path superelipse preenchendo [bounds] com raio [r] px. */
    fun path(r: Float, bounds: RectF): Path {
        val path = Path()
        val width = bounds.width()
        val height = bounds.height()
        val radius = r.coerceAtMost(width / 2f).coerceAtMost(height / 2f)
        if (width <= 0f || height <= 0f) return path

        // Quadrante unitário da curva, pré-computado uma vez (cos/sin são
        // caros pra repetir por canto; 28 segmentos já é liso a olho nu).
        val qx = FloatArray(STEPS + 1)
        val qy = FloatArray(STEPS + 1)
        for (i in 0..STEPS) {
            val t = (i.toDouble() / STEPS) * (PI / 2)
            qx[i] = cos(t).pow(2.0 / N).toFloat()
            qy[i] = sin(t).pow(2.0 / N).toFloat()
        }

        fun corner(cx: Float, cy: Float, sx: Float, sy: Float, moveTo: Boolean) {
            for (i in 0..STEPS) {
                val px = cx + sx * radius * qx[i]
                val py = cy + sy * radius * qy[i]
                if (moveTo && i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
        }

        // Sentido horário: sup. esq. → topo → sup. dir. → base → fecho.
        // As retas entre cantos são os lineTo implícitos entre quadrantes.
        corner(radius, radius, -1f, -1f, moveTo = true)
        corner(width - radius, radius, 1f, -1f, moveTo = false)
        corner(width - radius, height - radius, 1f, 1f, moveTo = false)
        corner(radius, height - radius, -1f, 1f, moveTo = false)
        path.close()
        return path
    }

    /** Outline provider que recorta o view num squircle (com clipToOutline). */
    fun outline(rPx: Float): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (view.width <= 0 || view.height <= 0) return
            val b = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
            outline.setPath(path(rPx, b))
        }
    }
}
