package com.tunegrab.app.lg

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * O vidro do Liquid Glass (tunegrab-lg v0.23.0): pintura em 4 camadas,
 * desenhadas por código porque NENHUM drawable XML combina as quatro:
 *
 *   1. FILL translúcido com gradiente sutil (o vidro nunca é chato: topo
 *      mais claro que a base — a luz "assenta" em cima).
 *   2. SPECULAR no ombro superior esquerdo: halo branco radial, o reflexo
 *      da tela na superfície do vidro (iOS 26 puro).
 *   3. ABERRAÇÃO CROMÁTICA na borda (o pedido do autor): stroke com
 *      SweepGradient espalhando vermelho quente e ciano frio em lados
 *      opostos do perímetro — a "lente" separa a luz nas bordas. Alpha
 *      baixo: tem que ser sentido, não visto.
 *   4. HAIRLINE branco (8%) por cima: o fio do vidro.
 *
 * Combinar com Squircle.outline() no view pra o conteúdo clipar igual.
 */
class GlassPaintDrawable(
    private val radiusPx: Float,
    /** Alpha do fill (0-255): menu/sheet 232, botão 210, card 200. */
    private val fillAlpha: Int = 232
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chroma = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x14FFFFFF
    }
    private var built = false

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val d = android.content.res.Resources.getSystem().displayMetrics.density
        if (!built) build(b.width().toFloat(), b.height().toFloat(), d)

        val rect = RectF(b)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, spec)

        val inset = 0.8f * d
        canvas.drawRoundRect(
            RectF(rect).apply { inset(inset, inset) }, radiusPx, radiusPx, chroma
        )
        val h = 0.5f * d
        canvas.drawRoundRect(
            RectF(rect).apply { inset(h, h) }, radiusPx, radiusPx, hairline
        )
    }

    private fun build(w: Float, h: Float, density: Float) {
        built = true

        // 1. Fill: topo mais claro, base mais escura e opaca
        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                (0x2E * fillAlpha / 255) shl 24 or 0xFFFFFF,
                (0x14 * fillAlpha / 255) shl 24 or 0xFFFFFF,
                (0xE6 * fillAlpha / 255) shl 24 or 0x0E0E10
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. Specular: halo radial no ombro superior esquerdo
        spec.shader = RadialGradient(
            w * 0.15f, -h * 0.1f, max(w, h) * 0.9f,
            intArrayOf(0x26FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // 3. Aberração cromática: sweep no perímetro, quente a Leste e
        //    frio a Oeste (0 radianos do SweepGradient = 3 horas)
        chroma.strokeWidth = 1.6f * density
        chroma.shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(
                0x00FF6B5E,
                0x50FF6B5E,
                0x1AFFD2A6,
                0x00FFFFFF,
                0x465AD8FF,
                0x1AA8B8FF,
                0x00FF6B5E
            ),
            floatArrayOf(0f, 0.14f, 0.24f, 0.42f, 0.62f, 0.76f, 1f)
        )

        hairline.strokeWidth = 1f * density
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(cf: ColorFilter?) {
        // cores fixas por design
    }
}
