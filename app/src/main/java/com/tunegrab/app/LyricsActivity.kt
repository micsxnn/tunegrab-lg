package com.tunegrab.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import com.tunegrab.app.lg.lyrics.LyricsScreen
import com.tunegrab.app.lg.lyrics.LyricsUiState
import com.tunegrab.app.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * GERADOR DE LETRA (v0.22.7, pedido do autor: "Faça o gerador de letra")
 * — REFEITO na v0.24.0-lg com AS BIBLIOTECAS DO LASTWAVE (Task 121):
 * a tela inteira agora é Compose (kyant0:backdrop + ModernLyricsView
 * portado): capa em ambiente é a fonte, topo/controles são vidro de
 * verdade e a letra acende em karaoke com falloff/blur/trail.
 *
 * O QUE NÃO MUDOU (a casa segue de pé):
 *  - conversa com o MESMO PlaybackService (a música NUNCA para nem
 *    recomeça — aqui é só interface, igual na PlayerActivity);
 *  - LyricsFetcher → LRCLIB com a escada de busca provada;
 *  - a faixa pulou (fila, notificação, auto-advance)? O tick percebe que
 *    o serviço mudou de faixa e busca a letra da nova SOZINHO;
 *  - toque num verso pula a música pra ele;
 *  - instrumental / sem letra / sem internet seguem com mensagem.
 */
class LyricsActivity : AppCompatActivity() {


    // MESMO padrão da PlayerActivity: bind no serviço de reprodução — a
    // música continua do jeito que está, a tela só LÊ posição/título/URI
    private var playback: PlaybackService? = null
    private var bound = false
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            playback = (service as? PlaybackService.LocalBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playback = null
        }
    }

    // ---------- estado observado pelo Compose (tudo em mutableState) ----------
    private var uiState by mutableStateOf<LyricsUiState>(
        LyricsUiState.Loading
    )
    private var screenTitle by mutableStateOf("")
    private var positionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var playing by mutableStateOf(false)
    private var canNext by mutableStateOf(false)
    private var canPrevious by mutableStateOf(false)
    private var artwork by mutableStateOf<ImageBitmap?>(null)

    // estado interno (fora do Compose)
    private var loadedKey: String? = null      // uri da faixa cuja letra está na tela
    private var artworkKey: String? = null     // uri cuja capa já está na tela
    private var loadJob: Job? = null
    private var artJob: Job? = null
    private var initialDuration = 0

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            syncWithService()
            tickHandler.postDelayed(this, 60)   // cadência do LastWave (60ms)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TELA INTEIRA EM COMPOSE (activity-compose setContent): a capa vira
        // ambiente, o vidro do LastWave refrata ela, a letra acende em karaoke
        setContent {
            LyricsScreen(
                title = screenTitle,
                artwork = artwork,
                state = uiState,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = playing,
                hasNext = canNext,
                hasPrevious = canPrevious,
                onSeek = { ms -> playback?.seekTo(ms.toInt()) },
                onToggle = { playback?.toggle() },
                onNext = { playback?.skipNext() },
                onPrevious = { playback?.skipPrevious() },
                onRetry = { reloadFromService() },
                onBack = { finish() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        bindService(
            Intent(this, PlaybackService::class.java),
            serviceConn,
            Context.BIND_AUTO_CREATE,
        )
        bound = true

        // primeira letra: a faixa que veio do player
        initialDuration = intent.getIntExtra(EXTRA_DURATION_MS, 0)
        val uriStr = intent.getStringExtra(EXTRA_URI)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        screenTitle = title
        if (uriStr != null) {
            loadedKey = uriStr
            load(uriStr, title)
        } else {
            uiState = LyricsUiState.NotFound
        }
        tickHandler.post(tick)
    }

    /**
     * Coração da sincronia, roda a cada 60ms (cadência do LastWave):
     *  - o serviço mudou de faixa? Busca a letra e a capa da nova SOZINHO;
     *  - mesma faixa? Atualiza posição/duração/play/fila pro karaoke acender.
     */
    private fun syncWithService() {
        val svc = playback ?: return
        val uri = svc.currentUri()?.toString()
        if (uri != null && uri != loadedKey) {
            loadedKey = uri
            val title = svc.currentTitle()
            screenTitle = title
            load(uri, title)
        }
        if (uri == null || uri != loadedKey) return
        if (screenTitle != svc.currentTitle()) {
            screenTitle = svc.currentTitle()
        }
        positionMs = svc.position().toLong().coerceAtLeast(0)
        val dur = svc.duration()
        if (dur > 0) durationMs = dur.toLong()
        playing = svc.isPlaying()
        canNext = svc.hasNext()
        canPrevious = svc.hasPrevious()
        if (uri != artworkKey) loadArtwork(uri)
    }

    /** Busca a letra da faixa [uriStr] (lavagem do título + LRCLIB em IO). */
    private fun load(uriStr: String, title: String) {
        loadJob?.cancel()
        uiState = LyricsUiState.Loading
        loadJob = lifecycleScope.launch {
            val durMs = try {
                playback?.duration()?.takeIf { it > 0 } ?: initialDuration
            } catch (ignored: Throwable) {
                initialDuration
            }
            val q = withContext(Dispatchers.IO) {
                LyricsFetcher.buildQuery(applicationContext, title, uriStr, durMs)
            }
            try {
                val res = LyricsFetcher.fetch(q)
                if (loadedKey != uriStr) return@launch
                uiState = when {
                    res == null -> LyricsUiState.NotFound
                    res.instrumental && res.synced == null && res.plain.isBlank() ->
                        LyricsUiState.Instrumental
                    res.synced != null -> LyricsUiState.Synced(res.synced)
                    else -> LyricsUiState.Plain(res.plain)
                }
            } catch (ignored: IOException) {
                if (loadedKey != uriStr) return@launch
                uiState = LyricsUiState.Error
            } catch (ignored: Exception) {
                if (loadedKey != uriStr) return@launch
                uiState = LyricsUiState.Error
            }
        }
    }

    /** Botão "Tentar de novo": refaz a busca da faixa que está tocando. */
    private fun reloadFromService() {
        val uri = playback?.currentUri()?.toString() ?: loadedKey ?: return
        loadedKey = uri
        load(uri, playback?.currentTitle() ?: screenTitle)
    }

    /**
     * Capa da faixa (fonte do vidro): MESMA lógica do loadCover da
     * PlayerActivity — MediaMetadataRetriever em IO, só arquivo LOCAL
     * (stream remoto não tem capa embutida). Sem capa = gradiente da marca.
     */
    private fun loadArtwork(uriStr: String) {
        artworkKey = uriStr
        artJob?.cancel()
        artJob = lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                if (uriStr.startsWith("http")) return@withContext null
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(applicationContext, android.net.Uri.parse(uriStr))
                    r.embeddedPicture
                } catch (ignored: Throwable) {
                    null
                } finally {
                    try {
                        r.release()
                    } catch (ignored: Throwable) {
                    }
                }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (artworkKey != uriStr) return@launch
            artwork = bmp?.asImageBitmap()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickHandler.removeCallbacks(tick)
        loadJob?.cancel()
        artJob?.cancel()
        if (bound) {
            try {
                unbindService(serviceConn)
            } catch (ignored: IllegalArgumentException) {
            }
            bound = false
        }
        // sem stopNow: a música segue tocando — esta tela só olhava
        playback = null
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DURATION_MS = "duration_ms"

        /** Abre a tela da letra pra faixa que o player está tocando. */
        fun start(ctx: Context, uri: String, title: String, durationMs: Int): Intent =
            Intent(ctx, LyricsActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DURATION_MS, durationMs)
    }
}
