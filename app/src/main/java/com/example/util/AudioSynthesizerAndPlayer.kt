package com.example.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import java.util.concurrent.Executors

object AudioSynthesizerAndPlayer {
    private const val TAG = "AudioSynthesizer"
    private val dtmfExecutor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Volatile
    private var sharedToneGenerator: ToneGenerator? = null

    fun playDialpadDtmfTone(digit: Char, enabled: Boolean) {
        if (!enabled) return
        val toneType = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> ToneGenerator.TONE_DTMF_1
        }
        try {
            dtmfExecutor.execute {
                synchronized(lock) {
                    try {
                        val generator = sharedToneGenerator ?: ToneGenerator(
                            AudioManager.STREAM_DTMF,
                            75
                        ).also { sharedToneGenerator = it }
                        generator.startTone(toneType, 90)
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to play DTMF tone", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DTMF executor error", e)
        }
    }
}
