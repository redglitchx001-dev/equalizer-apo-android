package com.equalizerapo.android.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.equalizerapo.android.dsp.BiquadFilter
import com.equalizerapo.android.model.ApoPreset
import kotlin.math.log10
import kotlin.math.pow

class EqVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPreset: ApoPreset? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 28f
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Neon Green
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3300E676") // Translucent Green
        style = Paint.Style.FILL
    }

    private val minFreq = 20f
    private val maxFreq = 20000f
    private val minDb = -20f
    private val maxDb = 20f

    fun setPreset(preset: ApoPreset) {
        this.currentPreset = preset
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // Draw Grid Lines
        drawGrid(canvas, w, h)

        // Draw Frequency Response Curve
        currentPreset?.let { preset ->
            drawCurve(canvas, preset, w, h)
        }
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        // Frequencies for vertical grid lines
        val freqGrid = floatArrayOf(20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f)
        for (f in freqGrid) {
            val x = freqToX(f, w)
            canvas.drawLine(x, 0f, x, h, gridPaint)
            if (f == 100f || f == 1000f || f == 10000f) {
                val label = if (f >= 1000f) "${(f / 1000).toInt()}k" else "${f.toInt()}"
                canvas.drawText(label, x + 5f, h - 15f, textPaint)
            }
        }

        // dB vertical scale grid lines
        val dbGrid = floatArrayOf(-15f, -10f, -5f, 0f, 5f, 10f, 15f)
        for (db in dbGrid) {
            val y = dbToY(db, h)
            canvas.drawLine(0f, y, w, y, gridPaint)
            canvas.drawText("${db.toInt()}dB", 10f, y - 5f, textPaint)
        }
    }

    private fun drawCurve(canvas: Canvas, preset: ApoPreset, w: Float, h: Float) {
        val biquads = preset.filters.filter { it.enabled }.map { BiquadFilter.fromEqFilter(it) }
        val path = Path()
        val fillPath = Path()

        var firstPoint = true
        fillPath.moveTo(0f, dbToY(0f, h))

        val step = 100
        for (i in 0..step) {
            val ratio = i / step.toFloat()
            // Logarithmic frequency sampling
            val freq = minFreq * (maxFreq / minFreq).pow(ratio)
            val x = freqToX(freq, w)

            var totalDb = preset.preampDb
            for (biquad in biquads) {
                totalDb += biquad.getMagnitudeDb(freq)
            }

            val y = dbToY(totalDb, h)

            if (firstPoint) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
                firstPoint = false
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(w, dbToY(0f, h))
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, curvePaint)
    }

    private fun freqToX(freq: Float, width: Float): Float {
        val logMin = log10(minFreq)
        val logMax = log10(maxFreq)
        val logF = log10(freq)
        return width * (logF - logMin) / (logMax - logMin)
    }

    private fun dbToY(db: Float, height: Float): Float {
        val clamped = db.coerceIn(minDb, maxDb)
        return height * (1f - (clamped - minDb) / (maxDb - minDb))
    }
}
