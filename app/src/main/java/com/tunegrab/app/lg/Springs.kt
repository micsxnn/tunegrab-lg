package com.tunegrab.app.lg

import android.content.Context
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Molas do Liquid Glass (tunegrab-lg v0.23.0): o iOS não usa easing — usa
 * SPRING. Tudo que aparece, aperta ou solta quica um tiquinho e assenta.
 * dynamicanimation dá o modelo físico (massa/ stiffness/ damping) que
 * reproduz o UIKit `spring` com dampingRatio 0.7-0.8.
 */
object Springs {

    /** Spring de aparecimento: escala 0.86 → 1 com quique sutil. */
    fun popIn(view: View, from: Float = 0.86f) {
        view.scaleX = from
        view.scaleY = from
        scale(view, 1f, stiffness = SpringForce.STIFFNESS_MEDIUM, damping = 0.72f)
    }

    /** Spring de desaparecimento: encolhe e some (menu fechando). */
    fun popOut(view: View, to: Float = 0.9f, endAction: (() -> Unit)? = null) {
        scale(view, to, stiffness = SpringForce.STIFFNESS_HIGH, damping = 1f) {
            view.alpha = 0f
            endAction?.invoke()
        }
    }

    /** Apertinho de toque: 0.94 → 1 (botões de vidro "afundam"). */
    fun press(view: View) {
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        scale(view, 1f, stiffness = SpringForce.STIFFNESS_HIGH, damping = 0.6f)
    }

    fun scale(
        view: View,
        target: Float,
        stiffness: Float = SpringForce.STIFFNESS_MEDIUM,
        damping: Float = 0.75f,
        endAction: (() -> Unit)? = null
    ) {
        val x = SpringAnimation(view, DynamicAnimation.SCALE_X, target).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = damping
        }
        val y = SpringAnimation(view, DynamicAnimation.SCALE_Y, target).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = damping
        }
        if (endAction != null) {
            x.addEndListener { _, canceled, _, _ ->
                if (!canceled) endAction.invoke()
            }
        }
        x.start()
        y.start()
    }

    /** translationY com mola (sheets/entradas de cima ou de baixo). */
    fun translateY(view: View, target: Float, stiffness: Float = SpringForce.STIFFNESS_LOW) {
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, target).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = 0.8f
            start()
        }
    }

    /** Haptic estilo iOS: toque curto e seco (UIImpactFeedback light). */
    fun haptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun dp(context: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    fun dpInt(context: Context, v: Int): Int = dp(context, v.toFloat()).toInt()
}
