package com.example.picomatch3vr.p3a7c9f1

import android.media.SoundPool
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.picomatch3vr.p3a7c9f1.ui.game.Match3EffectAudioPlayer
import com.example.picomatch3vr.p3a7c9f1.ui.game.Match3EffectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioDeviceSmokeTest {
    @Test
    fun samplesDecodeAndPlayOnHeadset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: Match3EffectAudioPlayer
        instrumentation.runOnMainSync {
            player = Match3EffectAudioPlayer(instrumentation.targetContext)
        }
        fun field(name: String): Any =
            player.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(player)!!
        val lock = field("lock")
        try {
            val deadline = SystemClock.elapsedRealtime() + 5000L
            var loaded = 0
            while (SystemClock.elapsedRealtime() < deadline) {
                loaded = synchronized(lock) { (field("loadedSoundIds") as Set<*>).size }
                if (loaded == 13) break
                SystemClock.sleep(15)
            }
            assertEquals("All original WAV assets must decode", 13, loaded)
            assertTrue((field("failedSoundIds") as Set<*>).isEmpty())
            val pool = field("soundPool") as SoundPool
            val samples = synchronized(lock) { (field("loadedSoundIds") as Set<*>).toList() }
            for (id in samples) {
                val stream = pool.play(id as Int, 0.08f, 0.08f, 1, 0, 1f)
                assertTrue("SoundPool must accept each sample", stream > 0)
                pool.stop(stream)
            }
            for (cascade in listOf(Int.MIN_VALUE, 0, 1, 2, 3, 4, 5, 6, 7, 8, Int.MAX_VALUE)) {
                instrumentation.runOnMainSync {
                    player.play(Match3EffectKind.Normal, cascade)
                }
                SystemClock.sleep(250)
            }
            for (kind in Match3EffectKind.entries.drop(1)) {
                instrumentation.runOnMainSync { player.play(kind, 2) }
                SystemClock.sleep(1200)
            }
            instrumentation.runOnMainSync { player.playLevelComplete() }
            SystemClock.sleep(2000)
            instrumentation.runOnMainSync {
                player.release()
                player.release()
                player.play(Match3EffectKind.Normal)
                player.playLevelComplete()
            }
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }
}
