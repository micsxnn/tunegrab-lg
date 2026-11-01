package com.tunegrab.app.lg.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunegrab.app.LyricLine
import com.tunegrab.app.R
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * O KARAOKÉ DO LASTWAVE, portado pro TuneGrab (Task 121).
 * Fonte: ui/player/ModernLyricsView.kt deles (ModernSyncedLyricsList), com
 * os MESMOS números que fazem a letra "andar como música":
 *
 *  - falloff por distância da linha cantada: alpha [1, .50, .40, .30, .22]
 *    e blur [0, .8, 1.4, 2.0, 2.6]dp — profundidade de campo;
 *  - foco por cor e luz, não por tamanho: linha ativa 1.0, vizinhas 0.98;
 *  - trilha elástica: quando a linha avança, as de baixo atrasam e entram
 *    uma a uma (atraso 38ms por linha, spring 0.72/180);
 *  - onda de início: a linha que COMEÇA a ser cantada pulsa +2% (senóide
 *    de 480ms) — o "impacto" da entrada do verso;
 *  - auto-scroll leva a linha ativa a 22% do topo da tela em 350–500ms
 *    (duração cresce com a distância) e RESPEITA quem rolou pra reler
 *    (600ms tocando / 5s parado);
 *  - toque no verso pula a música pra ele;
 *  - máscara de fade no topo (36dp) e embaixo (110dp).
 *
 * Diferenças honestas: nosso LRC é por LINHA (o parser da LRCLIB nossa),
 * então o word-by-word deles não entra; a linha cantada acende em OURO
 * (#F5D161, a marca) em vez de branco; tipografia Inter do app.
 */

// números EXATOS do ModernLyricsView.kt do LastWave
private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.50f, 0.40f, 0.30f, 0.22f)
private val LINE_FALLOFF_BLUR_DP = floatArrayOf(0f, 0.8f, 1.4f, 2.0f, 2.6f)
private val LYRIC_EASE = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SCROLL_MIN_MS = 350f
private const val LYRIC_SCROLL_MAX_MS = 500f

private val LYRIC_FONT = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Estilo da letra ativa (ouro, 600) e das vizinhas (branco, 500). */
object KaraokeStyle {
    val activeColor = Color(0xFFF5D161)
    val inactiveColor = Color.White
    val activeStyle = TextStyle(
        fontFamily = LYRIC_FONT,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    )
    val normalStyle = TextStyle(
        fontFamily = LYRIC_FONT,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    )
    val plainStyle = TextStyle(
        fontFamily = LYRIC_FONT,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.sp,
    )
}

/** Última linha cujo tempo já chegou (start-based, igual ao deles). */
internal fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var idx = -1
    for (i in lines.indices) {
        if (lines[i].timeMs <= positionMs) idx = i else break
    }
    return idx
}

/** Máscara de fade: topo some em [topFadeDp], base em [bottomFadeDp]. */
fun Modifier.lyricsFadingEdge(topFadeDp: Dp, bottomFadeDp: Dp): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val topPx = topFadeDp.toPx()
            val bottomPx = bottomFadeDp.toPx()
            val h = size.height
            if (h <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0f),
                        (topPx / h).coerceIn(0f, 1f) to Color.Black,
                        ((h - bottomPx) / h).coerceIn(0f, 1f) to Color.Black,
                        1f to Color.Black.copy(alpha = 0f),
                    ),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * Scroll com duração controlada (o animateScrollBy do Compose não aceita
 * duração — o LastWave anima frame a frame com o easing deles; port igual:
 * easing LYRIC_EASE, 350–500ms conforme a distância).
 */
private suspend fun LazyListState.animateScrollWithDuration(pixels: Float) {
    if (abs(pixels) < 0.5f) return
    val duration = abs(pixels).coerceIn(LYRIC_SCROLL_MIN_MS, LYRIC_SCROLL_MAX_MS)
    var consumed = 0f
    var lastFrame = 0L
    var elapsed = 0f
    scroll {
        while (true) {
            withFrameMillis { now ->
                if (lastFrame != 0L) elapsed += (now - lastFrame)
                lastFrame = now
            }
            val progress = (elapsed / duration).coerceIn(0f, 1f)
            val target = pixels * LYRIC_EASE.transform(progress)
            val delta = target - consumed
            if (delta != 0f) consumed += dispatchRawDelta(delta)
            if (progress >= 1f) break
        }
    }
}

private fun lyricScrollDurationMs(deltaPx: Float): Float =
    (abs(deltaPx) * 0.55f).coerceIn(LYRIC_SCROLL_MIN_MS, LYRIC_SCROLL_MAX_MS)

/** Texto básico do Compose sem material3 — a cor vai no style. */
@Composable
private fun KaraokeText(text: String, style: TextStyle, color: Color) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style.copy(color = color),
    )
}

/**
 * A LISTA KARAOKÉ — port direto do ModernSyncedLyricsList do LastWave
 * (letra por linha; ours LRC = linha, a deles também tem modo linha).
 */
@Composable
fun KaraokeLyricsList(
    lines: List<LyricLine>,
    currentPositionMs: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var userScrolledTime by remember { mutableLongStateOf(0L) }
    val currentPositionState by rememberUpdatedState(currentPositionMs)

    val activeIndex by remember(lines) {
        derivedStateOf { activeLineIndex(lines, currentPositionState()) }
    }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    if (isDragged) {
        userScrolledTime = System.currentTimeMillis()
    }

    LaunchedEffect(activeIndex, isPlaying) {
        // Auto-follow cede ao usuário por um tempo (600ms tocando, 5s parado)
        // e leva a linha ativa a 22% do topo — números do LastWave.
        val resumeDelayMs = if (isPlaying) 600L else 5000L
        val sinceUserScroll = System.currentTimeMillis() - userScrolledTime
        if (sinceUserScroll > resumeDelayMs && activeIndex in lines.indices) {
            runCatching {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportSize.height
                if (viewportHeight > 0) {
                    val targetY = (viewportHeight * 0.22f).toInt()
                    val targetItem = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
                    if (targetItem != null) {
                        val delta = targetItem.offset - targetY
                        listState.animateScrollWithDuration(delta.toFloat())
                    } else {
                        listState.animateScrollToItem(index = activeIndex, scrollOffset = -targetY)
                    }
                }
            }
        }
    }

    // Quebra de estrofe: silêncio >= 2400ms entre linhas (ou >= 3800 brutos)
    // ganha respiro extra em cima — igual ao deles.
    val stanzaBreaks = remember(lines) {
        BooleanArray(lines.size).also { breaks ->
            for (i in 1 until lines.size) {
                val prev = lines[i - 1]
                val curr = lines[i]
                val gap = curr.timeMs - prev.timeMs
                breaks[i] = prev.text.isBlank() || curr.text.isBlank() || gap >= 2400L
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.lyricsFadingEdge(topFadeDp = 36.dp, bottomFadeDp = 110.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 140.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(
            lines,
            key = { index, line -> "$index:${line.timeMs}" },
        ) { index, line ->
            val isNewStanza = stanzaBreaks.getOrElse(index) { false }
            val isActive = index == activeIndex
            val isPast = activeIndex >= 0 && index < activeIndex
            val distance = abs(index - activeIndex)

            // Foco por cor/luz, não por tamanho: ativa 1.0, vizinhas 0.98.
            val scaleState = animateFloatAsState(
                targetValue = if (isActive) 1f else 0.98f,
                animationSpec = tween(400, easing = LYRIC_EASE),
                label = "tgLyricScale_$index",
            )
            val alphaState = animateFloatAsState(
                targetValue = LINE_FALLOFF_ALPHA[distance.coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)],
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "tgLyricAlpha_$index",
            )
            // Profundidade de campo: longe da linha cantada amacia; volta a
            // ficar nítido quando o usuário está folheando.
            val blurState = animateFloatAsState(
                targetValue = if (isActive || isDragged || activeIndex < 0) 0f
                else LINE_FALLOFF_BLUR_DP[distance.coerceAtMost(LINE_FALLOFF_BLUR_DP.lastIndex)],
                animationSpec = tween(380, easing = FastOutSlowInEasing),
                label = "tgLyricBlur_$index",
            )
            // Trilha elástica: as linhas de baixo entram uma a uma.
            val trail = remember { Animatable(0f) }
            LaunchedEffect(activeIndex) {
                val rel = index - activeIndex
                if (isPlaying && !isDragged && activeIndex > 0 && rel in 0..6) {
                    trail.snapTo(7f + rel * 3f)
                    delay(rel * 38L)
                    trail.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 180f))
                } else if (trail.value != 0f) {
                    trail.snapTo(0f)
                }
            }
            // Onda de início: pulso senoidal de 480ms na entrada do verso.
            val onset = remember { Animatable(0f) }
            LaunchedEffect(isActive, isPlaying) {
                if (isActive && isPlaying) {
                    onset.snapTo(0f)
                    onset.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                } else {
                    onset.snapTo(0f)
                }
            }
            val onsetWave = if (isActive && isPlaying && onset.value in 0.001f..0.999f) {
                (kotlin.math.sin(kotlin.math.PI.toFloat() * onset.value))
            } else 0f

            val translationYState = animateFloatAsState(
                targetValue = if (isActive) -3.5f else if (isPast) -1.5f else 3.5f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "tgLyricY_$index",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isNewStanza) 22.dp else 0.dp)
                    .graphicsLayer {
                        val totalScale = scaleState.value * (1f + 0.020f * onsetWave)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = totalScale
                        scaleY = totalScale
                        this.translationY = (translationYState.value + trail.value) * density
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            val r = blurState.value * density
                            renderEffect = if (r > 0.3f) {
                                android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            } else null
                        }
                        this.alpha = alphaState.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onSeek(line.timeMs.toLong())
                    }
                    .padding(horizontal = 4.dp, vertical = if (isActive) 4.dp else 2.dp),
            ) {
                KaraokeText(
                    text = line.text.ifBlank { "·" },
                    style = if (isActive) KaraokeStyle.activeStyle else KaraokeStyle.normalStyle,
                    color = if (isActive) KaraokeStyle.activeColor else KaraokeStyle.inactiveColor,
                )
            }
        }
    }
}

/** Letra SIMPLES (sem tempo): texto inteiro, estilo ModernPlainLyricsView. */
@Composable
fun PlainLyricsView(text: String, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 40.dp, bottom = 160.dp, start = 28.dp, end = 28.dp),
    ) {
        item {
            KaraokeText(
                text = text,
                style = KaraokeStyle.plainStyle,
                color = Color.White.copy(alpha = 0.88f),
            )
        }
    }
}
