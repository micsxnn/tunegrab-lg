package com.tunegrab.app.lg.lyrics

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunegrab.app.LyricLine
import com.tunegrab.app.R
import com.tunegrab.app.lg.glass.LiquidGlassContainer
import com.tunegrab.app.lg.glass.LiquidGlassIconButton
import com.tunegrab.app.lg.glass.SquircleShape
import com.tunegrab.app.lg.glass.isDeviceGlassCapable
import com.tunegrab.app.lg.glass.layerBackdropCompat
import com.tunegrab.app.lg.glass.rememberGlassBackdrop

/** Ouro da marca — a única cor de acento do app (One Gold Rule). */
private val GOLD = Color(0xFFF5D161)
private val OLED = Color(0xFF050506)

/**
 * ESTADO DA TELA DA LETRA (port simplificado do LyricsUiState do LastWave —
 * sealed interface imutável; o que muda de estado é a tela inteira).
 */
sealed interface LyricsUiState {
    /** Buscando na LRCLIB. */
    data object Loading : LyricsUiState

    /** Letra sincronizada (LRC) — o karaoke acende. */
    data class Synced(val lines: List<LyricLine>) : LyricsUiState

    /** Só texto, sem tempo. */
    data class Plain(val text: String) : LyricsUiState

    /** Faixa instrumental. */
    data object Instrumental : LyricsUiState

    /** Não achou ninguém cantando isso. */
    data object NotFound : LyricsUiState

    /** Rede caída / API fora. */
    data object Error : LyricsUiState
}

/**
 * A TELA DA LETRA DO TUNEGRAB LG — o Now Playing de letras do LastWave,
 * com as bibliotecas DELES (kyant0:backdrop): a capa inunda o fundo em
 * blur e É A FONTE do vidro; a barra de topo, o cartão de estado e o
 * painel de controles são vidro de verdade refratando esse fundo.
 *
 * Estrutura (irmãos, nunca filhos — contrato do backdrop):
 *   Box raiz
 *    ├─ AmbientBackdrop  ← layerBackdropCompat(backdrop) = FONTE
 *    └─ Coluna: barra de vidro / letra / painel de vidro
 */
@Composable
fun LyricsScreen(
    title: String,
    artwork: ImageBitmap?,
    state: LyricsUiState,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberGlassBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OLED),
    ) {
        // ── FONTE DO VIDRO: a capa em ambiente (blur + scrim) ──────────────
        AmbientBackdrop(
            artwork = artwork,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropCompat(backdrop),
        )

        // ── CONTEÚDO ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            GlassTopBar(
                title = title,
                backdrop = backdrop,
                onBack = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (state) {
                    is LyricsUiState.Synced -> KaraokeLyricsList(
                        lines = state.lines,
                        currentPositionMs = { positionMs },
                        isPlaying = isPlaying,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is LyricsUiState.Plain -> PlainLyricsView(
                        text = state.text,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> StateCard(
                        state = state,
                        backdrop = backdrop,
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            GlassControlPanel(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                backdrop = backdrop,
                onSeek = onSeek,
                onToggle = onToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * A CAPA INUNDA O FUNDO — port do FluidArtworkBackground do LastWave e do
 * nosso AmbientBackdrop do player: bitmap reduzido pra ~56px (blur barato
 * em qualquer aparelho) + Modifier.blur(48dp) na API 31+ + scrim vertical
 * (leve em cima, quase OLED embaixo). SEM capa: gradiente dourado escuro.
 */
@Composable
private fun AmbientBackdrop(artwork: ImageBitmap?, modifier: Modifier = Modifier) {
    val glass = isDeviceGlassCapable()
    val small = remember(artwork) { artwork?.let { downscale(it, 56) } }

    Box(modifier) {
        if (small != null) {
            Image(
                bitmap = small,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(48.dp)
                        } else {
                            Modifier
                        },
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3A2E10),
                                OLED,
                            ),
                            center = Offset(0.5f, 0.18f),
                            radius = 1400f,
                        ),
                    ),
            )
        }
        // scrim de legibilidade: em cima leve, embaixo afunda no OLED
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OLED.copy(alpha = 0.42f),
                            OLED.copy(alpha = 0.60f),
                            OLED.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
    }
}

private fun downscale(src: ImageBitmap, target: Int): ImageBitmap {
    val w = src.width
    val h = src.height
    if (w <= target && h <= target) return src
    val scale = target.toFloat() / maxOf(w, h)
    val bmp = src.asAndroidBitmap()
    val out = android.graphics.Bitmap.createScaledBitmap(
        bmp,
        (w * scale).toInt().coerceAtLeast(1),
        (h * scale).toInt().coerceAtLeast(1),
        true,
    )
    return out.asImageBitmap()
}

/** Barra de topo: botão voltar em vidro + título (estilo Now Playing). */
@Composable
private fun GlassTopBar(
    title: String,
    backdrop: com.kyant.backdrop.Backdrop?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassIconButton(
            backdrop = backdrop,
            iconRes = R.drawable.ic_back,
            contentDescription = null,
            onClick = onBack,
        )
        Spacer(Modifier.size(12.dp))
        BasicTextTitle(
            text = title,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BasicTextTitle(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = KaraokeStyle.activeStyle.copy(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * PAINEL DE CONTROLES EM VIDRO — port do ModernLyricsControls do LastWave:
 * pílula squircle de vidro com progresso fino (trilha 4dp, bolinha 12dp
 * iOS), tempos e transporte (anterior / play-pause / próximo).
 */
@Composable
private fun GlassControlPanel(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    backdrop: com.kyant.backdrop.Backdrop?,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        shape = SquircleShape(28.dp),
        interactive = false,
        luminance = 0.42f,
        blurScale = 0.9f,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            GlassSlider(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicTime(text = formatTime(positionMs))
                BasicTime(text = "-" + formatTime((durationMs - positionMs).coerceAtLeast(0)))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    iconRes = R.drawable.ic_skip_previous,
                    enabled = hasPrevious,
                    backdrop = backdrop,
                    onClick = onPrevious,
                )
                Spacer(Modifier.size(18.dp))
                LiquidGlassIconButton(
                    backdrop = backdrop,
                    iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    contentDescription = null,
                    onClick = onToggle,
                    modifier = Modifier.size(64.dp),
                    iconSize = 28.dp,
                )
                Spacer(Modifier.size(18.dp))
                TransportButton(
                    iconRes = R.drawable.ic_skip_next,
                    enabled = hasNext,
                    backdrop = backdrop,
                    onClick = onNext,
                )
            }
            Spacer(Modifier.height(8.dp))
            // crédito da fonte — honestidade com quem empresta a letra
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.text.BasicText(
                    text = stringResource(R.string.lyrics_credit),
                    style = KaraokeStyle.plainStyle.copy(
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun TransportButton(
    iconRes: Int,
    enabled: Boolean,
    backdrop: com.kyant.backdrop.Backdrop?,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, label = "transportAlpha")
    LiquidGlassIconButton(
        backdrop = backdrop,
        iconRes = iconRes,
        contentDescription = null,
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(48.dp),
        iconSize = 24.dp,
        tint = Color.White.copy(alpha = alpha),
    )
}

@Composable
private fun BasicTime(text: String) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = KaraokeStyle.plainStyle.copy(
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.66f),
        ),
    )
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/**
 * Slider iOS (port do VolumeSlider do LastWave): trilha 4dp branca
 * translúcida, preenchimento branco, bolinha 12dp. Toque pula direto;
 * arrastar dá preview e soltar chama onSeek.
 */
@Composable
private fun GlassSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { boxSize = it }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0 && boxSize.width > 0) {
                        onSeek(((offset.x / boxSize.width).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (boxSize.width > 0) {
                            dragFraction = (offset.x / boxSize.width).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        dragFraction?.let { f ->
                            if (durationMs > 0) onSeek((f * durationMs).toLong())
                        }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    if (boxSize.width > 0) {
                        dragFraction = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // trilha
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        )
        // preenchimento
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f)),
        )
        // bolinha
        val knobX = with(LocalDensity.current) { (boxSize.width * fraction).toDp() }
        Box(
            modifier = Modifier
                .offset(x = knobX - 6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Cartão central de estado (carregando/erro/instrumental) em vidro. */
@Composable
private fun StateCard(
    state: LyricsUiState,
    backdrop: com.kyant.backdrop.Backdrop?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message: String
    val showRetry: Boolean
    val showProgress: Boolean
    when (state) {
        is LyricsUiState.Loading -> {
            message = ""
            showRetry = false
            showProgress = true
        }
        is LyricsUiState.NotFound -> {
            message = stringResource(R.string.lyrics_not_found)
            showRetry = true
            showProgress = false
        }
        is LyricsUiState.Error -> {
            message = stringResource(R.string.lyrics_error)
            showRetry = true
            showProgress = false
        }
        is LyricsUiState.Instrumental -> {
            message = stringResource(R.string.lyrics_instrumental)
            showRetry = false
            showProgress = false
        }
        else -> {
            message = ""
            showRetry = false
            showProgress = false
        }
    }

    LiquidGlassContainer(
        backdrop = backdrop,
        shape = SquircleShape(24.dp),
        interactive = false,
        luminance = 0.42f,
        modifier = modifier.padding(horizontal = 40.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showProgress) {
                RingProgress()
                Spacer(Modifier.height(12.dp))
            }
            if (message.isNotEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    text = message,
                    style = KaraokeStyle.plainStyle.copy(
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            if (showRetry) {
                Spacer(Modifier.height(16.dp))
                LiquidGlassActionButton(
                    backdrop = backdrop,
                    label = stringResource(R.string.lyrics_retry),
                    onClick = onRetry,
                )
            }
        }
    }
}

/** Anel de progresso dourado (o spinner do app é XML; aqui é Canvas). */
@Composable
private fun RingProgress() {
    Canvas(Modifier.size(40.dp)) {
        val stroke = 3.5.dp.toPx()
        drawArc(
            color = Color.White.copy(alpha = 0.16f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = GOLD,
            startAngle = -90f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Pílula "Tentar de novo" em vidro — port do botão retry (bg_lyrics_pill
 * XML virou vidro de verdade).
 */
@Composable
fun LiquidGlassActionButton(
    backdrop: com.kyant.backdrop.Backdrop?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        shape = SquircleShape(999.dp),
        modifier = modifier,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = KaraokeStyle.normalStyle.copy(
                fontSize = 13.sp,
                color = Color.White,
            ),
            modifier = Modifier
                .pointerInput(onClick) { detectTapGestures { onClick() } }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** TextStyle exposto pro Activity (título no rodapé etc., se precisar). */
val LyricsTextStyle: TextStyle get() = KaraokeStyle.plainStyle
