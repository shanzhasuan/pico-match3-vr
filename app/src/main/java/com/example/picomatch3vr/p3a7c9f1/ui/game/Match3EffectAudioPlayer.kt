package com.example.picomatch3vr.p3a7c9f1.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.example.picomatch3vr.p3a7c9f1.R
import java.util.ArrayDeque

class Match3EffectAudioPlayer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()

    private val soundIds: Map<Match3EffectKind, List<Int>>
    private val loadedSoundIds = mutableSetOf<Int>()
    private val failedSoundIds = mutableSetOf<Int>()
    private val pending = ArrayDeque<Playback>()
    private val levelCompleteSoundId: Int
    private var released = false

    init {
        // Register before loading so a fast load cannot lose its completion callback.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(lock) {
                if (!released) {
                    if (status == 0) {
                        loadedSoundIds += sampleId
                    } else {
                        failedSoundIds += sampleId
                    }
                    val iterator = pending.iterator()
                    val now = SystemClock.elapsedRealtime()
                    while (iterator.hasNext()) {
                        val playback = iterator.next()
                        if (now > playback.expiresAt || playback.soundId == sampleId) {
                            iterator.remove()
                            if (status == 0 && playback.soundId == sampleId && now <= playback.expiresAt) {
                                playNow(playback)
                            }
                        }
                    }
                }
            }
        }
        soundIds =
            mapOf(
                Match3EffectKind.Normal to listOf(
                    load(R.raw.match3_pop),
                    load(R.raw.match3_pop_b),
                    load(R.raw.match3_pop_c),
                    load(R.raw.match3_pop_d),
                    load(R.raw.match3_pop_e),
                    load(R.raw.match3_pop_f),
                    load(R.raw.match3_pop_g),
                    load(R.raw.match3_pop_h),
                ),
                Match3EffectKind.Line to listOf(load(R.raw.match3_line)),
                Match3EffectKind.Bomb to listOf(load(R.raw.match3_bomb)),
                Match3EffectKind.ColorBomb to listOf(load(R.raw.match3_colorbomb)),
                Match3EffectKind.Combo to listOf(load(R.raw.match3_combo)),
            )
        levelCompleteSoundId = load(R.raw.match3_win)
    }

    private fun load(resourceId: Int): Int = soundPool.load(appContext, resourceId, 1)

    fun play(kind: Match3EffectKind, cascadeCount: Int = 1) {
        synchronized(lock) {
            if (released) return
            val samples = soundIds[kind] ?: return
            val soundId =
                if (kind == Match3EffectKind.Normal) {
                    samples[cascadeCount.coerceIn(1, samples.size) - 1]
                } else {
                    samples.first()
                }
            val volume =
                when (kind) {
                    Match3EffectKind.Normal -> 0.56f
                    Match3EffectKind.Line -> 0.59f
                    Match3EffectKind.Bomb -> 0.60f
                    Match3EffectKind.ColorBomb -> 0.58f
                    Match3EffectKind.Combo -> 0.62f
                }
            enqueueOrPlay(
                Playback(
                    soundId = soundId,
                    volume = volume,
                    rate = 1f,
                    priority = 1,
                    expiresAt = SystemClock.elapsedRealtime() + 300L,
                ),
            )
        }
    }

    private fun enqueueOrPlay(playback: Playback) {
        if (playback.soundId == 0 || playback.soundId in failedSoundIds) return
        if (playback.soundId in loadedSoundIds) {
            playNow(playback)
        } else {
            if (pending.size >= 4) pending.removeFirst()
            pending.addLast(playback)
        }
    }

    private fun playNow(playback: Playback) {
        soundPool.play(
            playback.soundId,
            playback.volume,
            playback.volume,
            playback.priority,
            0,
            playback.rate,
        )
    }

    fun playLevelComplete() {
        synchronized(lock) {
            if (released) return
            enqueueOrPlay(
                Playback(
                    soundId = levelCompleteSoundId,
                    volume = 0.64f,
                    rate = 1f,
                    priority = 2,
                    expiresAt = SystemClock.elapsedRealtime() + 1500L,
                ),
            )
        }
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            pending.clear()
            loadedSoundIds.clear()
            failedSoundIds.clear()
            soundPool.setOnLoadCompleteListener(null)
            soundPool.release()
        }
    }

    private data class Playback(
        val soundId: Int,
        val volume: Float,
        val rate: Float,
        val priority: Int,
        val expiresAt: Long,
    )

}
