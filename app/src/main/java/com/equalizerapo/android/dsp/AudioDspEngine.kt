package com.equalizerapo.android.dsp

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.equalizerapo.android.model.ApoPreset
import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType

/**
 * High-performance DSP engine linking Equalizer APO filters to Android's AudioEffect system.
 */
class AudioDspEngine(private val context: Context) {

    private val TAG = "AudioDspEngine"
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var isEnabled = false

    // Default center frequencies for 10-band Equalizer fallback
    val standardFrequencies = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun initAudioSession(audioSessionId: Int = 0) {
        release()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                initDynamicsProcessing(audioSessionId)
            } else {
                initFallbackEqualizer(audioSessionId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native AudioEffect", e)
            initFallbackEqualizer(audioSessionId)
        }
    }

    private fun initDynamicsProcessing(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        val bandCount = 10
        val eqConfig = DynamicsProcessing.Eq(true, true, bandCount)
        
        for (i in 0 until bandCount) {
            val band = DynamicsProcessing.EqBand(true, standardFrequencies[i], 0f)
            eqConfig.setBand(i, band)
        }

        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // Stereo
            true, 1, // PreEQ enabled, 1 band count
            false, 0, // Mbc
            true, 1, // PostEQ enabled
            false // Limiter
        ).build()

        dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
            enabled = isEnabled
        }
    }

    private fun initFallbackEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Equalizer initialization error", e)
        }
    }

    fun applyPreset(preset: ApoPreset) {
        if (!isEnabled) return

        val activeFilters = preset.filters.filter { it.enabled }
        val biquads = activeFilters.map { BiquadFilter.fromEqFilter(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
            applyToDynamicsProcessing(preset.preampDb, biquads)
        } else if (equalizer != null) {
            applyToEqualizer(preset.preampDb, biquads)
        }
    }

    private fun applyToDynamicsProcessing(preampDb: Float, biquads: List<BiquadFilter>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        
        // Calculate combined magnitude at standard frequencies
        for (i in standardFrequencies.indices) {
            val freq = standardFrequencies[i]
            var totalGainDb = preampDb

            for (biquad in biquads) {
                totalGainDb += biquad.getMagnitudeDb(freq)
            }

            // Clamp gain between -15 dB and +15 dB for safety
            val clampedGain = totalGainDb.coerceIn(-15f, 15f)
            try {
                dynamicsProcessing?.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, freq, clampedGain))
            } catch (e: Exception) {
                Log.w(TAG, "Error setting band $i gain", e)
            }
        }
    }

    private fun applyToEqualizer(preampDb: Float, biquads: List<BiquadFilter>) {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange // e.g. [-1500, 1500] in millibels

        for (band in 0 until numBands) {
            val centerFreqHz = eq.getCenterFreq(band.toShort()) / 1000f
            var totalGainDb = preampDb

            for (biquad in biquads) {
                totalGainDb += biquad.getMagnitudeDb(centerFreqHz)
            }

            val millibels = (totalGainDb * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
            try {
                eq.setBandLevel(band.toShort(), millibels.toShort())
            } catch (e: Exception) {
                Log.w(TAG, "Error setting equalizer band $band", e)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        dynamicsProcessing?.enabled = enabled
        equalizer?.enabled = enabled
    }

    fun release() {
        try {
            dynamicsProcessing?.release()
            dynamicsProcessing = null
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
    }
}
