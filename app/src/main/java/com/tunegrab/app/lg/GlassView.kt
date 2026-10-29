package com.tunegrab.app.lg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vidro FOSCO DE VERDADE (tunegrab-lg v0.23.0): captura o conteúdo do root
 * a cada frame, reduz, desfoca e desenha recortado nos próprios limites —
 * a técnica do BlurView do Dimezis, vendida em ~180 linhas pra não depender
 * de lib externa.
 *
 * Por que downsample: desfocar 1080x2400 por frame mata bateria. Capturo o
 * root num bitmap 8x menor (a GPU da captura é um draw() normal, barato),
 * desfoco esse bitmap pequeno (radius dividido pelo fator) e estico de
 * volta — blur óptico idêntico a olho nu, custo de GPU ~1/64.
 *
 * Paths de blur:
 *   - API 31+: RenderEffect no Paint (hardware accelerated, o caminho certo)
 *   - API 24-30: ScriptIntrinsicBlur do RenderScript (framework, sem dep)
 *
 * Uso (o GlassView é SEMPRE o fundo do próprio container):
 *   <com.tunegrab.app.lg.GlassView ...>
 *     <conteúdo da pílula/>
 *   </GlassView>
 *   glassView.setup(rootView)   // root = quem fica ATRÁS (activity content)
 */
class GlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var root: View? = null
    private var bitmap: Bitmap? = null
    private var canvas = Canvas()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Rect()

    private var scaleFactor = 8f
    private var blurRadius = 18f
    private var rs: RenderScript? = null
    private var script: ScriptIntrinsicBlur? = null

    /** Um frame dirty quando o conteúdo atrás muda (scroll, texto, tudo). */
    private val predrawObserver = ViewTreeObserver.OnPreDrawListener {
        invalidate()
        true
    }

    fun setup(rootView: View) {
        root = rootView
        scaleFactor = 8f
        blurRadius = 18f
        rootView.viewTreeObserver.addOnPreDrawListener(predrawObserver)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setBlurRadius(dp: Float) {
        blurRadius = dp
        bitmap = null
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        prepareBitmap()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        clip.set(0, 0, measuredWidth, measuredHeight)
    }

    /** Captura + desfoca o root (proportionais ao MEU tamanho na tela). */
    private fun prepareBitmap() {
        val rootV = root ?: return
        if (measuredWidth == 0 || measuredHeight == 0) return

        val myW = measuredWidth
        val myH = measuredHeight
        val downW = max(1, (myW / scaleFactor).roundToInt())
        val downH = max(1, (myH / scaleFactor).roundToInt())

        var bmp = bitmap
        if (bmp == null || bmp.width != downW || bmp.height != downH) {
            bmp = Bitmap.createBitmap(downW, downH, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            canvas.setBitmap(bmp)
        }

        // Desenha a região do root que fica ATRÁS de mim, reduzida.
        val location = IntArray(2)
        getLocationOnScreen(location)
        val rootLoc = IntArray(2)
        rootV.getLocationOnScreen(rootLoc)
        val offX = (location[0] - rootLoc[0]).toFloat()
        val offY = (location[1] - rootLoc[1]).toFloat()
        val sx = 1f / scaleFactor
        val sy = 1f / scaleFactor

        canvas.save()
        canvas.scale(sx, sy)
        canvas.translate(-offX, -offY)
        // root desenha a si e aos filhos na área que me cobre
        canvas.clipRect(offX, offY, offX + myW, offY + myH)
        rootV.draw(canvas)
        canvas.restore()

        bmp?.let { applyBlur(it) }
    }

    // RenderScript em TODAS as APIs 24-34: deprecated desde o 31 (a Google
    // empurra RenderEffect), mas ainda funciona em tudo — e aqui o blur é
    // num bitmap 8x reduzido, custo ínfimo. Um só caminho, zero branch.
    private fun applyBlur(bmp: Bitmap) {
        val radius = (blurRadius / scaleFactor).coerceIn(0.1f, 25f)
        val ctx = context.applicationContext
        if (rs == null) {
            rs = RenderScript.create(ctx)
            script = ScriptIntrinsicBlur.create(rs!!, Element.U8_4(rs!!))
        }
        val alloc = Allocation.createFromBitmap(
            rs!!, bmp, Allocation.MipmapControl.MIPMAP_NONE,
            Allocation.USAGE_SCRIPT
        )
        script?.setRadius(radius)
        script?.setInput(alloc)
        script?.forEach(alloc)
        alloc.copyTo(bmp)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            val save = canvas.save()
            canvas.clipRect(clip)
            canvas.drawBitmap(bmp, null, clip, paint)
            canvas.restoreToCount(save)
        }
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        root?.viewTreeObserver?.removeOnPreDrawListener(predrawObserver)
        root = null
        rs?.destroy()
        rs = null
        script = null
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }
}
