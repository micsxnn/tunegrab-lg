package com.tunegrab.app.lg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * FUNDO AMBIENTE DO PLAYER (tunegrab-lg v0.23.1-lg): é isto que o iOS 26 faz
 * no Now Playing do Apple Music — a capa NÃO fica presa no cartão dela, ela
 * "vaza" pela tela inteira como um borrão vivo e saturado atrás de tudo.
 *
 * Como aqui é um View de fundo (nunca clicável, sempre atrás):
 *  - API 31+: o bitmap pequeno é esticado pra cobrir a tela e o GPU faz o
 *    resto — RenderEffect de blur 48px + saturação 1.6 (o vidro de verdade
 *    do iOS também SATURA o que desfoca, não só esmaece).
 *  - API 24-30: sem RenderEffect — a mesma técnica do generativo do Apple
 *    Music no papel: bitmap reduzido a ~48px de largura; o upscale bilinear
 *    do draw já é um blur de fábrica.
 *  - Sem capa: brilho radial dourado da marca (a capa bg_album_art é o
 *    gatilho visual do Início; aqui ele respira no fundo inteiro).
 *
 * O bitmap de entrada é reduzido pra ~96px de largura ANTES de tudo: o
 * desenho final é um borrão — não existe motivo pra manter megapixels.
 */
class AmbientBackdrop @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var art: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var defaultShader: Shader? = null

    /** Capa nova tocando: substitui o ambiente com a cor da faixa. */
    fun setArtwork(bitmap: Bitmap?) {
        val small = bitmap?.let { downscale(it, 96) }
        if (small === art) return
        art = small
        applyEffect()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        defaultShader = if (w > 0 && h > 0) {
            RadialGradient(
                w * 0.5f, h * 0.28f, max(w, h) * 0.9f,
                intArrayOf(0x66F5D161, 0x22C9974C, 0x00000000),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        } else null
    }

    private fun applyEffect() {
        val b = art ?: run {
            if (Build.VERSION.SDK_INT >= 31) setRenderEffect(null)
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val blur = RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP)
            val sat = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.6f) })
            setRenderEffect(RenderEffect.createColorFilterEffect(sat, blur))
        } else {
            // API 24-30: borrão por redução — 96px já era pequeno, desce mais
            // um degrau; o upscale bilinear do onDraw fecha o efeito
            art = downscale(b, 40)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = art
        if (b == null) {
            defaultShader?.let {
                paint.shader = it
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
            return
        }
        // COVER com folga 1.25×: o borrão ultrapassa as bordas (o iOS deixa
        // o ambiente "respirar" pra fora; encostar reto na borda mata o efeito)
        src.set(0, 0, b.width, b.height)
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = max(vw / b.width, vh / b.height) * 1.25f
        val dw = b.width * scale
        val dh = b.height * scale
        dst.set((vw - dw) / 2f, (vh - dh) / 2f, (vw + dw) / 2f, (vh + dh) / 2f)
        canvas.drawBitmap(b, src, dst, paint)

        // piso escuro embaixo: o painel de vidro e os tempos precisam de
        // contraste — o Music do iOS também escurece a base do Now Playing
        val floor = LinearGradient(
            0f, vh * 0.55f, 0f, vh,
            intArrayOf(0x00000000, 0x8A050507.toInt(), 0xD2050507.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = floor
        canvas.drawRect(0f, vh * 0.55f, vw, vh, paint)
        paint.shader = null
        paint.colorFilter = null
    }

    /** Reduz o bitmap mantendo o aspect (memória e custo de blur caem juntos). */
    private fun downscale(bmp: Bitmap, targetW: Int): Bitmap {
        if (bmp.width <= targetW) return bmp
        val ratio = targetW.toFloat() / bmp.width
        return Bitmap.createScaledBitmap(
            bmp, targetW, max(1, (bmp.height * ratio).toInt()), true
        )
    }
}
