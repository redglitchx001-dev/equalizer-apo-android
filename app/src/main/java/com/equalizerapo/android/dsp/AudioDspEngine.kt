package com.equalizerapo.android.dsp

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import com.equalizerapo.android.model.ApoPreset
import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Multi-Session DSP Engine supporting global session 0 + dynamic app sessions.
 */
class AudioDspEngine(private val context: Context) {

    private val TAG = "AudioDspEngine"
    private var isEnabled = true
    private var currentPreset: ApoPreset? = null

    // Map of SessionID to AudioEffect instances
    private val activeEqualizers = ConcurrentHashMap<Int, Equalizer>()
    private val activeDynamicsProcessors = ConcurrentHashMap<Int, DynamicsProcessing>()
    private val activeLoudnessEnhancers = ConcurrentHashMap<Int, LoudnessEnhancer>()
    private val activeBassBoosts = ConcurrentHashMap<Int, BassBoost>()

    val standardFrequencies = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun attachSession(sessionId: Int) {
        if (activeEqualizers.containsKey(sessionId) || activeDynamicsProcessors.containsKey(sessionId)) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val bandCount = 10
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // Stereo
                    true, bandCount, // PreEQ enabled with 10 bands
                    false, 0,
                    true, bandCount, // PostEQ enabled with 10 bands
                    false
                ).build()

                val dp = DynamicsProcessing(0, sessionId, config).apply {
                    enabled = isEnabled
                }
                activeDynamicsProcessors[sessionId] = dp
            }
        } catch (e: Exception) {
            Log.w(TAG, "DynamicsProcessing not supported on session $sessionId, falling back to Equalizer", e)
        }

        try {
            val eq = Equalizer(0, sessionId).apply {
                enabled = isEnabled
            }
            activeEqualizers[sessionId] = eq
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Equalizer on session $sessionId", e)
        }

        try {
            val le = LoudnessEnhancer(sessionId).apply {
                enabled = isEnabled
            }
            activeLoudnessEnhancers[sessionId] = le
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer not available on session $sessionId", e)
        }

        try {
            val bb = BassBoost(0, sessionId).apply {
                enabled = isEnabled
            }
            activeBassBoosts[sessionId] = bb
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost not available on session $sessionId", e)
        }

        // Apply current preset to newly attached session
        currentPreset?.let { applyPresetToSession(sessionId, it) }
    }

    fun detachSession(sessionId: Int) {
        if (sessionId == 0) return // Keep session 0 alive

        activeDynamicsProcessors.remove(sessionId)?.apply {
            try { enabled = false; release() } catch (e: Exception) {}
        }
        activeEqualizers.remove(sessionId)?.apply {
            try { enabled = false; release() } catch (e: Exception) {}
        }
        activeLoudnessEnhancers.remove(sessionId)?.apply {
            try { enabled = false; release() } catch (e: Exception) {}
        }
        activeBassBoosts.remove(sessionId)?.apply {
            try { enabled = false; release() } catch (e: Exception) {}
        }
    }

    fun applyPreset(preset: ApoPreset) {
        this.currentPreset = preset
        if (!isEnabled) return

        activeEqualizers.keys.forEach { sessionId ->
            applyPresetToSession(sessionId, preset)
        }
        activeDynamicsProcessors.keys.forEach { sessionId ->
            applyPresetToSession(sessionId, preset)
        }
    }

    private fun applyPresetToSession(sessionId: Int, preset: ApoPreset) {
        val activeFilters = preset.filters.filter { it.enabled }
        val biquads = activeFilters.map { BiquadFilter.fromEqFilter(it) }

        // Apply Preamp gain using LoudnessEnhancer
        activeLoudnessEnhancers[sessionId]?.let { le ->
            try {
                // LoudnessEnhancer gain is in mB (millibels), 1 dB = 100 mB
                val preampMb = (preset.preampDb * 100).toInt().coerceIn(-3000, 3000)
                le.setTargetGain(preampMb)
                le.enabled = isEnabled && preset.preampDb != 0f
            } catch (e: Exception) {
                Log.w(TAG, "Error applying preamp gain to session $sessionId", e)
            }
        }

        // Apply DynamicsProcessing Eq Bands
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activeDynamicsProcessors[sessionId]?.let { dp ->
                try {
                    dp.enabled = isEnabled
                    for (i in standardFrequencies.indices) {
                        val freq = standardFrequencies[i]
                        var totalGainDb = 0f

                        for (biquad in biquads) {
                            totalGainDb += biquad.getMagnitudeDb(freq)
                        }

                        val clampedGain = totalGainDb.coerceIn(-24f, 24f)
                        val band = DynamicsProcessing.EqBand(true, freq, clampedGain)
                        dp.setPreEqBandAllChannelsTo(i, band)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error applying DynamicsProcessing to session $sessionId", e)
                }
            }
        }

        // Apply Equalizer Bands (Fallback / Concurrent)
        activeEqualizers[sessionId]?.let { eq ->
            try {
                eq.enabled = isEnabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange

                for (band in 0 until numBands) {
                    val centerFreqHz = eq.getCenterFreq(band.toShort()) / 1000f
                    var totalGainDb = 0f

                    for (biquad in biquads) {
                        totalGainDb += biquad.getMagnitudeDb(centerFreqHz)
                    }

                    val millibels = (totalGainDb * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(band.toShort(), millibels.toShort())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error applying Equalizer to session $sessionId", e)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        activeDynamicsProcessors.values.forEach { try { it.enabled = enabled } catch (e: Exception) {} }
        activeEqualizers.values.forEach { try { it.enabled = enabled } catch (e: Exception) {} }
        activeLoudnessEnhancers.values.forEach { try { it.enabled = enabled } catch (e: Exception) {} }
        activeBassBoosts.values.forEach { try { it.enabled = enabled } catch (e: Exception) {} }

        currentPreset?.let { applyPreset(it) }
    }

    fun release() {
        activeDynamicsProcessors.keys.toList().forEach { detachSession(it) }
        activeEqualizers.keys.toList().forEach { detachSession(it) }
    }
}
