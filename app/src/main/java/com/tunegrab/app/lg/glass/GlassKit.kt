package com.tunegrab.app.lg.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * KIT LIQUID GLASS DO TUNEGRAB LG (Task 121) — port 1:1 da receita do
 * LastWave-Native (ui/theme/LiquidGlass.kt deles), com as MESMAS bibliotecas:
 * io.github.kyant0:backdrop 2.0.1 (o Liquid Glass em Compose — captura o
 * conteúdo de trás e aplica blur/cor/lente em tempo real) e io.github.kyant0:
 * shapes 1.2.1 no gradle.
 *
 * A receita (GlassRecipe) é a deles, sem mudar um valor: blur 12dp que
 * estica pra 20dp sobre conteúdo claro e encolhe pra 6dp no escuro, brilho
 * +0.05, saturação 1.1, lente de borda = menor dimensão / 6, véu preto
 * 0.18→0.56 e, no toque, o vidro AFUNDA (blur +2dp, escala de mola e um
 * brilho radial que segue o dedo — BlendMode.Plus).
 *
 * Diferenças honestas pro nosso app: tema ÚNICO escuro (OLED), então
 * isDark é sempre true; sem flag de Configurações (o fork LG É vidro);
 * fallbacks usam cinza de elevação nosso em vez do M3; ícones vêm dos
 * drawables do próprio app (ic_back, ic_play…) — zero dependência nova
 * de ícone.
 */

/** Capabilidade de vidro real: RenderEffect (API 31+), GPU e RAM decente. */
@Composable
fun isDeviceGlassCapable(): Boolean {
    val view = LocalView.current
    if (view.isInEditMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (!view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val am = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (am?.isLowRamDevice == true) return false
    return true
}

/**
 * Lembre um LayerBackdrop (a FONTE do vidro). O elemento marcado com
 * [layerBackdropCompat] é o que os vidros irmãos refratam; o [onDraw]
 * opcional roda antes do conteúdo (o LastWave pinta a cor de fundo por
 * baixo, evitando linha fantasma na borda).
 * Sem capabilidade devolve null — e TODA a chamada cai no fallback sólido.
 */
@Composable
fun rememberGlassBackdrop(
    onDraw: ContentDrawScope.() -> Unit = { drawContent() },
): LayerBackdrop? {
    if (!isDeviceGlassCapable()) return null
    return com.kyant.backdrop.backdrops.rememberLayerBackdrop(onDraw = onDraw)
}

/** Marca um composable como FONTE que os vidros irmãos refratam. */
fun Modifier.layerBackdropCompat(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null) return this
    return this.layerBackdrop(backdrop)
}

/**
 * Squircle G2 contínua (superelipse) — port do LastWave. Constantes cúbicas
 * descrevem o canto de curvatura contínua do iOS: k = 1.528665 estica o
 * controle até a metade da aresta.
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp) : this(
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tl = if (isLtr) topStart else topEnd
        val tr = if (isLtr) topEnd else topStart
        val br = if (isLtr) bottomEnd else bottomStart
        val bl = if (isLtr) bottomStart else bottomEnd

        if (tl <= 0f && tr <= 0f && br <= 0f && bl <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        }

        return Outline.Generic(createSquirclePath(w, h, tl, tr, br, bl))
    }
}

fun createSquirclePath(
    w: Float,
    h: Float,
    tlRadius: Float,
    trRadius: Float,
    brRadius: Float,
    blRadius: Float,
): Path {
    val path = Path()
    val maxRadius = minOf(w, h) / 2f
    val tl = tlRadius.coerceIn(0f, maxRadius)
    val tr = trRadius.coerceIn(0f, maxRadius)
    val br = brRadius.coerceIn(0f, maxRadius)
    val bl = blRadius.coerceIn(0f, maxRadius)

    val k = 1.528665f
    var lTl = tl * k
    var lTr = tr * k
    var lBr = br * k
    var lBl = bl * k

    val maxWTop = lTl + lTr
    if (maxWTop > w && maxWTop > 0f) {
        val scale = w / maxWTop
        lTl *= scale; lTr *= scale
    }
    val maxWBottom = lBl + lBr
    if (maxWBottom > w && maxWBottom > 0f) {
        val scale = w / maxWBottom
        lBl *= scale; lBr *= scale
    }
    val maxHLeft = lTl + lBl
    if (maxHLeft > h && maxHLeft > 0f) {
        val scale = h / maxHLeft
        lTl *= scale; lBl *= scale
    }
    val maxHRight = lTr + lBr
    if (maxHRight > h && maxHRight > 0f) {
        val scale = h / maxHRight
        lTr *= scale; lBr *= scale
    }

    path.moveTo(lTl, 0f)
    path.lineTo(w - lTr, 0f)
    if (lTr > 0.001f) {
        path.cubicTo(
            w - lTr * (1f - 0.712053f), 0f,
            w - lTr * (1f - 0.566789f), lTr * 0.030183f,
            w - lTr * (1f - 0.455953f), lTr * 0.087377f,
        )
        path.cubicTo(
            w - lTr * (1f - 0.345118f), lTr * 0.144571f,
            w - lTr * (1f - 0.242846f), lTr * 0.242846f,
            w - lTr * (1f - 0.144571f), lTr * 0.345118f,
        )
        path.cubicTo(
            w - lTr * (1f - 0.087377f), lTr * 0.455953f,
            w - lTr * 0.030183f, lTr * (1f - 0.566789f),
            w, lTr * (1f - 0.712053f),
        )
        path.lineTo(w, lTr)
    } else {
        path.lineTo(w, 0f)
        path.lineTo(w, lTr)
    }
    path.lineTo(w, h - lBr)
    if (lBr > 0.001f) {
        path.cubicTo(
            w, h - lBr * (1f - 0.712053f),
            w - lBr * 0.030183f, h - lBr * (1f - 0.566789f),
            w - lBr * 0.087377f, h - lBr * 0.455953f,
        )
        path.cubicTo(
            w - lBr * 0.144571f, h - lBr * (1f - 0.345118f),
            w - lBr * 0.242846f, h - lBr * 0.242846f,
            w - lBr * 0.345118f, h - lBr * 0.144571f,
        )
        path.cubicTo(
            w - lBr * 0.455953f, h - lBr * (1f - 0.087377f),
            w - lBr * (1f - 0.566789f), h - lBr * 0.030183f,
            w - lBr * (1f - 0.712053f), h,
        )
        path.lineTo(w - lBr, h)
    } else {
        path.lineTo(w, h)
        path.lineTo(w - lBr, h)
    }
    path.lineTo(lBl, h)
    if (lBl > 0.001f) {
        path.cubicTo(
            lBl * (1f - 0.712053f), h,
            lBl * (1f - 0.566789f), h - lBl * 0.030183f,
            lBl * (1f - 0.455953f), h - lBl * 0.087377f,
        )
        path.cubicTo(
            lBl * (1f - 0.345118f), h - lBl * 0.144571f,
            lBl * (1f - 0.242846f), h - lBl * 0.242846f,
            lBl * (1f - 0.144571f), h - lBl * 0.345118f,
        )
        path.cubicTo(
            lBl * (1f - 0.087377f), h - lBl * 0.455953f,
            lBl * 0.030183f, h - lBl * (1f - 0.566789f),
            0f, h - lBl * (1f - 0.712053f),
        )
        path.lineTo(0f, h - lBl)
    } else {
        path.lineTo(0f, h)
        path.lineTo(0f, h - lBl)
    }
    path.lineTo(0f, lTl)
    if (lTl > 0.001f) {
        path.cubicTo(
            0f, lTl * (1f - 0.712053f),
            lTl * 0.030183f, lTl * (1f - 0.566789f),
            lTl * 0.087377f, lTl * (1f - 0.455953f),
        )
        path.cubicTo(
            lTl * 0.144571f, lTl * (1f - 0.345118f),
            lTl * 0.242846f, lTl * 0.242846f,
            lTl * 0.345118f, lTl * 0.144571f,
        )
        path.cubicTo(
            lTl * 0.455953f, lTl * (1f - 0.087377f),
            lTl * (1f - 0.566789f), lTl * 0.030183f,
            lTl * (1f - 0.712053f), 0f,
        )
        path.lineTo(lTl, 0f)
    } else {
        path.lineTo(0f, 0f)
        path.lineTo(lTl, 0f)
    }
    path.close()
    return path
}

/** Presiona/segura: mola 0→1 (damping 0.5, stiffness 300) lida no draw. */
class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0 em repouso, 1 pressionado — lido no draw, sem recomposição. */
    val pressProgress: Float get() = pressAnimation.value

    /** Ponto do toque local — centro do brilho de pressão. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            ) { change, _ ->
                touchPosition = change.position
            }
        }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/** Fosco de fallback (sem GPU 31+/RAM baixa): cinza de elevação do app. */
@Composable
fun glassFallbackColor(): Color = Color(0xFF1E1E21).copy(alpha = 0.82f)

/**
 * A RECEITA DO VIDRO — coração portado do LastWave. Um único lugar define o
 * frost: pílulas, cartões e botões de vidro passam todos por aqui, então o
 * gelo nunca diverge entre superfícies.
 */
private object GlassRecipe {
    const val BLUR_BASE_DP = 12f
    const val BLUR_BRIGHT_DP = 20f
    const val BLUR_DARK_DP = 6f

    const val BRIGHTNESS = 0.05f
    const val CONTRAST = 1f
    const val SATURATION = 1.1f

    const val LENS_DIVISOR = 6f

    const val MIN_SCRIM = 0.18f
    const val MAX_SCRIM = 0.56f

    const val PRESS_BLUR_DP = 2f
}

/** A lente do kyant0 só aceita shape de canto — retângulo puro derruba o shader. */
internal fun lensSafeGlassShape(shape: Shape): Shape =
    if (shape == RectangleShape) RoundedCornerShape(0.dp) else shape

/**
 * Modifier de vidro interativo. O elemento TEM que ser irmão da fonte
 * (nunca filho) — contrato do backdrop do kyant0.
 *
 * [luminance] 0..1 = claridade média do conteúdo de trás (0.5 = neutro);
 * [blurScale] escala o frost (painéis grandes pedem menos).
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    luminance: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = GlassRecipe.MIN_SCRIM,
    maxScrim: Float = GlassRecipe.MAX_SCRIM,
): Modifier =
    this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { lensSafeGlassShape(shape) },
            highlight = { highlight },
            effects = {
                val l = (luminance * 2f - 1f).let { sign(it) * it * it }
                val press = interaction?.pressProgress ?: 0f
                colorControls(
                    brightness = GlassRecipe.BRIGHTNESS,
                    contrast = GlassRecipe.CONTRAST,
                    saturation = GlassRecipe.SATURATION,
                )
                blur(
                    (
                        if (l > 0f) {
                            lerp(
                                GlassRecipe.BLUR_BASE_DP.dp.toPx(),
                                GlassRecipe.BLUR_BRIGHT_DP.dp.toPx(),
                                l,
                            )
                        } else {
                            lerp(
                                GlassRecipe.BLUR_BASE_DP.dp.toPx(),
                                GlassRecipe.BLUR_DARK_DP.dp.toPx(),
                                -l,
                            )
                        }
                    ) * blurScale + GlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
                )
                lens(
                    size.minDimension / GlassRecipe.LENS_DIVISOR +
                        GlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
                    size.minDimension / 2f,
                    false,
                )
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
            },
            onDrawSurface = {
                val darken = lerp(minScrim, maxScrim, ((luminance - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * press),
                                Color.Transparent,
                            ),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val scale = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
        ).then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )

/**
 * liquidGlass composable: aplica a receita se o aparelho tem vidro real;
 * sem capabilidade cai no fosco sólido — o app NUNCA fica quebrado.
 * Tema do fork LG é escuro único: isDark fixo true (igual ao forceDark do
 * LastWave pros controles do Now Playing).
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop?,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    luminance: Float = 0.5f,
    blurScale: Float = 1f,
    minScrim: Float = GlassRecipe.MIN_SCRIM,
    maxScrim: Float = GlassRecipe.MAX_SCRIM,
    pressedScale: Float = 1.12f,
): Modifier {
    if (backdrop == null || !isDeviceGlassCapable()) {
        return this
            .clip(shape)
            .background(glassFallbackColor())
    }
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = true,
        backdrop = backdrop,
        luminance = luminance,
        shape = shape,
        interaction = if (interactive) interaction else null,
        pressedScale = pressedScale,
        highlight = highlight,
        blurScale = blurScale,
        minScrim = minScrim,
        maxScrim = maxScrim,
    )
}

/** Container de vidro com fallback sólido — base de cartões e painéis. */
@Composable
fun LiquidGlassContainer(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    luminance: Float = 0.5f,
    blurScale: Float = 1f,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    if (backdrop == null || !isDeviceGlassCapable()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(glassFallbackColor()),
            contentAlignment = contentAlignment,
            content = content,
        )
        return
    }
    Box(
        modifier = modifier.liquidGlass(
            backdrop,
            shape,
            interactive,
            highlight,
            luminance,
            blurScale,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Pílula de vidro flutuante que abriga botões de ícone (dock/controles). */
@Composable
fun LiquidGlassActionPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(24.dp),
    content: @Composable RowScope.() -> Unit,
) {
    if (backdrop == null || !isDeviceGlassCapable()) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(glassFallbackColor()),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    Row(
        modifier = modifier.liquidGlass(backdrop, shape, true, Highlight.Default),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Botão circular de vidro (voltar, play, próximo…). O ícone vem dos
 * drawables do app com tint — mesma arte nas telas View e Compose.
 */
@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    interactive: Boolean = true,
    iconSize: Dp = 20.dp,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        highlight = Highlight.Default,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(iconSize)
                .clickable(onClick = onClick),
        )
    }
}

/**
 * Reconhecedor de toque que NÃO consome o evento (port do DragGestureInspector
 * do catálogo do Kyant): o vidro reage ao dedo e o botão de baixo continua
 * recebendo o clique — os dois ao mesmo tempo.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent = drag(
            pointerId = down.id,
            onDrag = { onDrag(it, it.positionChange()) },
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointerId } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
