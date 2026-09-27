package com.equalizerapo.android.dsp

import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType
import kotlin.math.*

/**
 * Biquad IIR Filter calculations based on Audio EQ Cookbook formulas.
 * Used for DSP filtering and generating magnitude response curves.
 */
class BiquadFilter(
    val type: FilterType,
    val sampleRate: Float = 48000f,
    val frequency: Float,
    val gainDb: Float,
    val q: Float
) {
    var b0: Double = 1.0
    var b1: Double = 0.0
    var b2: Double = 0.0
    var a0: Double = 1.0
    var a1: Double = 0.0
    var a2: Double = 0.0

    init {
        calculateCoefficients()
    }

    private fun calculateCoefficients() {
        val w0 = 2.0 * Math.PI * frequency / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val A = 10.0.pow(gainDb / 40.0)

        when (type) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * A
                b1 = -2.0 * cos(w0)
                b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A
                a1 = -2.0 * cos(w0)
                a2 = 1.0 - alpha / A
            }
            FilterType.LOW_SHELF -> {
                val beta = sqrt(A) / q
                b0 = A * ((A + 1) - (A - 1) * cos(w0) + beta * sin(w0))
                b1 = 2 * A * ((A - 1) - (A + 1) * cos(w0))
                b2 = A * ((A + 1) - (A - 1) * cos(w0) - beta * sin(w0))
                a0 = (A + 1) + (A - 1) * cos(w0) + beta * sin(w0)
                a1 = -2 * ((A - 1) + (A + 1) * cos(w0))
                a2 = (A + 1) + (A - 1) * cos(w0) - beta * sin(w0)
            }
            FilterType.HIGH_SHELF -> {
                val beta = sqrt(A) / q
                b0 = A * ((A + 1) + (A - 1) * cos(w0) + beta * sin(w0))
                b1 = -2 * A * ((A - 1) + (A + 1) * cos(w0))
                b2 = A * ((A + 1) + (A - 1) * cos(w0) - beta * sin(w0))
                a0 = (A + 1) - (A - 1) * cos(w0) + beta * sin(w0)
                a1 = 2 * ((A - 1) - (A + 1) * cos(w0))
                a2 = (A + 1) - (A - 1) * cos(w0) - beta * sin(w0)
            }
            FilterType.LOW_PASS -> {
                b0 = (1 - cos(w0)) / 2.0
                b1 = 1 - cos(w0)
                b2 = (1 - cos(w0)) / 2.0
                a0 = 1 + alpha
                a1 = -2 * cos(w0)
                a2 = 1 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1 + cos(w0)) / 2.0
                b1 = -(1 + cos(w0))
                b2 = (1 + cos(w0)) / 2.0
                a0 = 1 + alpha
                a1 = -2 * cos(w0)
                a2 = 1 - alpha
            }
            FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cos(w0)
                b2 = 1.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cos(w0)
                a2 = 1.0 - alpha
            }
            else -> {
                b0 = 1.0; b1 = 0.0; b2 = 0.0
                a0 = 1.0; a1 = 0.0; a2 = 0.0
            }
        }
    }

    /**
     * Compute magnitude response in dB at frequency f (Hz).
     */
    fun getMagnitudeDb(f: Float): Float {
        if (type == FilterType.PREAMP) return gainDb
        val w = 2.0 * Math.PI * f / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2 * w)

        val numReal = (b0 / a0) + (b1 / a0) * cosW + (b2 / a0) * cos2W
        val numImag = -(b1 / a0) * sin(w) - (b2 / a0) * sin(2 * w)
        val denReal = 1.0 + (a1 / a0) * cosW + (a2 / a0) * cos2W
        val denImag = -(a1 / a0) * sin(w) - (a2 / a0) * sin(2 * w)

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq == 0.0) return 0f
        val mag = sqrt(numMagSq / denMagSq)
        return (20.0 * log10(max(mag, 1e-6))).toFloat()
    }

    companion object {
        fun fromEqFilter(filter: EqFilter, sampleRate: Float = 48000f): BiquadFilter {
            return BiquadFilter(
                type = filter.type,
                sampleRate = sampleRate,
                frequency = filter.frequency,
                gainDb = filter.gain,
                q = filter.qFactor
            )
        }
    }
}
