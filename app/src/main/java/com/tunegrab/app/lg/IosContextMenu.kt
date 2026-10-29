package com.tunegrab.app.lg

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tunegrab.app.R
import kotlin.math.max
import kotlin.math.min

/**
 * O MENU iOS (tunegrab-lg v0.23.0): o "abre meio que por cima" que o autor
 * pediu. Comportamento copiado do context menu do iOS 26:
 *
 *   1. A página inteira RECEDE: escala pra 0.94, ganha cantos arredondados
 *      (no iOS a página vira um "cartão" atrás do menu) e desfoca (API 31+;
 *      em Android 7-10 recede sem blur).
 *   2. Um scrim escuro sobe por cima da página.
 *   3. O menu DE VIDRO brota no ponto do toque: nasce 0.5x e estica pra 1
 *      com MOLA (quica), alpha acompanha.
 *   4. Toque fora, botão voltar ou escolha: tudo fecha com a inversa.
 *
 * API:
 *   IosContextMenu.show(activity, anchorView) {
 *     item(R.string.label, R.drawable.ic_x) { ... ação ... }
 *     item(R.string.apagar, destructive = true) { ... }
 *   }
 */
object IosContextMenu {

    /** Linha do menu: label (string res) + ação. destructive pinta de vermelho. */
    class Item(
        val labelRes: Int,
        val iconRes: Int? = null,
        val destructive: Boolean = false,
        val action: () -> Unit
    )

    /** Builder do DSL: item(...)/item(...){} dentro do show(). */
    class ContextMenuBuilder {
        internal val items = mutableListOf<Item>()
        fun item(
            labelRes: Int,
            iconRes: Int? = null,
            destructive: Boolean = false,
            action: () -> Unit
        ) {
            items.add(Item(labelRes, iconRes, destructive, action))
        }
    }

    private const val PAGE_SCALE = 0.94f
    private val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)

    private var open: Open? = null

    private class Open(
        val overlay: FrameLayout,
        val page: View,
        val menu: LinearLayout
    )

    fun show(activity: Activity, anchor: View, builder: ContextMenuBuilder.() -> Unit) {
        if (open != null) return
        val items = ContextMenuBuilder().apply(builder).items
        if (items.isEmpty()) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val page = content.getChildAt(0) ?: return
        val dp = { v: Float -> Springs.dp(activity, v) }
        val density = activity.resources.displayMetrics.density

        // ---------- overlay raiz (scrim + menu) ----------
        @SuppressLint("InflateParams")
        val overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // scrim: começa transparente e sobe (fade 180ms)
            setBackgroundColor(0x00000000)
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> dismiss(); true }
            // BACK fecha (o iOS fecha o menu com o gesto também)
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss(); true
                } else false
            }
        }
        content.addView(overlay)

        // ---------- 1. a página recede ----------
        page.pivotY = dp(80f) // iOS pivota pro topo (a página "senta")
        page.animate()
            .scaleX(PAGE_SCALE).scaleY(PAGE_SCALE)
            .setDuration(260L).setInterpolator(EASE)
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            page.outlineProvider = Squircle.outline(dp(24f))
            page.clipToOutline = true
            page.setRenderEffect(
                RenderEffect.createBlurEffect(
                    dp(22f), dp(22f), Shader.TileMode.CLAMP
                )
            )
        } else {
            page.outlineProvider = Squircle.outline(dp(24f))
            page.clipToOutline = true
        }
        // scrim com alpha animada (ValueAnimator simples, 180ms)
        android.animation.ValueAnimator.ofFloat(0f, 0.42f).apply {
            duration = 180L
            addUpdateListener {
                overlay.setBackgroundColor(
                    (it.animatedValue as Float * 255).toInt() shl 24
                )
            }
            start()
        }

        // ---------- 2. o menu de vidro ----------
        val menu = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassPaintDrawable(dp(22f), fillAlpha = 235)
            clipToOutline = true
            outlineProvider = Squircle.outline(dp(22f))
            elevation = dp(24f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        items.forEachIndexed { index, item ->
            if (index > 0) {
                menu.addView(
                    View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, max(1, dp(0.8f).toInt())
                        ).also { it.marginStart = dp(52f).toInt() }
                        setBackgroundColor(0x14FFFFFF)
                    }
                )
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18f).toInt(), 0, dp(18f).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48f).toInt()
                )
                isClickable = true
                isFocusable = true

                val labelColor = if (item.destructive) 0xFFFF6B5E.toInt()
                else ContextCompat.getColor(activity, R.color.on_background)

                item.iconRes?.let { iconRes ->
                    addView(
                        ImageView(activity).apply {
                            setImageResource(iconRes)
                            setColorFilter(labelColor)
                            layoutParams = LinearLayout.LayoutParams(
                                dp(21f).toInt(), dp(21f).toInt()
                            )
                        }
                    )
                }
                addView(
                    TextView(activity).apply {
                        setText(item.labelRes)
                        setTextColor(labelColor)
                        textSize = 15.5f
                        typeface = android.graphics.Typeface.create(
                            "sans-serif", android.graphics.Typeface.NORMAL
                        )
                        letterSpacing = -0.01f
                        layoutParams = LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                        ).also { l ->
                            l.marginStart = if (item.iconRes != null) dp(14f).toInt() else 0
                        }
                    }
                )

                setOnClickListener { v ->
                    Springs.haptic(v)
                    dismiss {
                        item.action()
                    }
                }
                // apertinho de vidro no toque
                setOnTouchListener { v, ev ->
                    if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
                        Springs.press(v)
                    }
                    v.performClick()
                    false
                }
            }
            menu.addView(row)
        }

        overlay.addView(
            menu,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // posiciona: acima do anchor se couber, senão abaixo; clampado 14dp
        menu.post {
            val anchorLoc = IntArray(2)
            anchor.getLocationOnScreen(anchorLoc)
            val contentLoc = IntArray(2)
            content.getLocationOnScreen(contentLoc)
            val anchorTopInContent = anchorLoc[1] - contentLoc[1]
            val anchorMidX = anchorLoc[0] - contentLoc[0] + anchor.width / 2f

            val mw = menu.measuredWidth
            val mh = menu.measuredHeight
            val margin = dp(14f)
            val roomAbove = anchorTopInContent
            val above = roomAbove > mh + margin * 2

            val x = (anchorMidX - mw / 2f)
                .coerceIn(margin, (content.width - mw - margin))
                .round()
            val y = if (above) {
                (anchorTopInContent - mh - dp(8f)).coerceAtLeast(margin + dp(8f)).round()
            } else {
                (anchorTopInContent + anchor.height + dp(8f))
                    .coerceAtMost(content.height - mh - margin).round()
            }

            (menu.layoutParams as FrameLayout.LayoutParams).let { lp ->
                lp.leftMargin = x
                lp.topMargin = y
                menu.layoutParams = lp
            }

            // mola de entrada: nasce no PONTO DO TOQUE (pivot no âncora)
            menu.pivotX = (anchorMidX - x).coerceIn(0f, mw.toFloat())
            menu.pivotY = if (above) mh.toFloat() else 0f
            menu.scaleX = 0.5f
            menu.scaleY = 0.5f
            menu.alpha = 0f
            Springs.popIn(menu, from = 0.5f)
            menu.animate().alpha(1f).setDuration(120L).start()
            Springs.haptic(menu)
        }

        open = Open(overlay, page, menu)
    }

    private fun Float.round(): Int = this.toInt()

    /** Fechamento com a inversa da entrada; [after] roda no fim (a ação). */
    fun dismiss(after: (() -> Unit)? = null) {
        val o = open ?: run { after?.invoke(); return }
        open = null

        // menu some encolhendo
        Springs.popOut(o.menu, to = 0.85f) {
            (o.overlay.parent as? ViewGroup)?.removeView(o.overlay)
        }
        o.menu.animate().alpha(0f).setDuration(140L).start()

        // página volta
        o.page.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(240L).setInterpolator(EASE)
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            o.page.setRenderEffect(null)
        }
        o.page.outlineProvider = null
        // clipToOutline volta a false DEPOIS da animação (cantos somem suave)
        o.page.postDelayed({ o.page.clipToOutline = false }, 240L)
        o.page.postDelayed({ after?.invoke() }, 160L)
    }
}
