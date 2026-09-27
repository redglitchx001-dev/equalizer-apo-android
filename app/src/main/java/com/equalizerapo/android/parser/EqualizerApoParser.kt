package com.equalizerapo.android.parser

import com.equalizerapo.android.model.ApoPreset
import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Parser for Equalizer APO and Peace GUI configuration files (config.txt).
 */
object EqualizerApoParser {

    private val PREAMP_PATTERN = Pattern.compile("Preamp:\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*dB", Pattern.CASE_INSENSITIVE)
    // Example: Filter 1: ON PK Fc 105 Hz Gain 4.5 dB Q 1.41
    private val FILTER_PATTERN = Pattern.compile(
        "Filter(?:\\s+\\d+)?:\\s*(ON|OFF)\\s+([A-Z]+)\\s+Fc\\s+(\\d+(?:\\.\\d+)?)\\s*Hz\\s+Gain\\s+([+-]?\\d+(?:\\.\\d+)?)\\s*dB(?:\\s+Q\\s+(\\d+(?:\\.\\d+)?))?",
        Pattern.CASE_INSENSITIVE
    )
    private val GRAPHIC_EQ_PATTERN = Pattern.compile("GraphicEQ:\\s*(.+)", Pattern.CASE_INSENSITIVE)

    fun parse(presetName: String, inputStream: InputStream): ApoPreset {
        val preset = ApoPreset(name = presetName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        var lineIndex = 0

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
                    parseLine(trimmed, preset, ++lineIndex)
                }
            }
        }
        return preset
    }

    fun parseString(presetName: String, content: String): ApoPreset {
        val preset = ApoPreset(name = presetName)
        var lineIndex = 0
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
                parseLine(trimmed, preset, ++lineIndex)
            }
        }
        return preset
    }

    private fun parseLine(line: String, preset: ApoPreset, lineIndex: Int) {
        // Match Preamp
        val preampMatcher = PREAMP_PATTERN.matcher(line)
        if (preampMatcher.find()) {
            val dbVal = preampMatcher.group(1)?.toFloatOrNull() ?: 0f
            preset.preampDb = dbVal
            return
        }

        // Match Standard Parametric Filter
        val filterMatcher = FILTER_PATTERN.matcher(line)
        if (filterMatcher.find()) {
            val statusStr = filterMatcher.group(1) ?: "ON"
            val typeCode = filterMatcher.group(2) ?: "PK"
            val fcStr = filterMatcher.group(3) ?: "1000"
            val gainStr = filterMatcher.group(4) ?: "0"
            val qStr = filterMatcher.group(5) ?: "1.414"

            val isEnabled = statusStr.equals("ON", ignoreCase = true)
            val type = FilterType.fromCode(typeCode)
            val fc = fcStr.toFloatOrNull() ?: 1000f
            val gain = gainStr.toFloatOrNull() ?: 0f
            val q = qStr.toFloatOrNull() ?: 1.414f

            val filter = EqFilter(
                id = preset.filters.size + 1,
                enabled = isEnabled,
                type = type,
                frequency = fc,
                gain = gain,
                qFactor = q
            )
            preset.filters.add(filter)
            return
        }

        // Match GraphicEQ
        val graphicMatcher = GRAPHIC_EQ_PATTERN.matcher(line)
        if (graphicMatcher.find()) {
            val pointsData = graphicMatcher.group(1) ?: ""
            val points = mutableListOf<Pair<Float, Float>>()
            val pairs = pointsData.split(";")
            for (pair in pairs) {
                val tokens = pair.trim().split("\\s+".toRegex())
                if (tokens.size >= 2) {
                    val freq = tokens[0].toFloatOrNull()
                    val gain = tokens[1].toFloatOrNull()
                    if (freq != null && gain != null) {
                        points.add(Pair(freq, gain))
                    }
                }
            }
            if (points.isNotEmpty()) {
                val graphicFilter = EqFilter(
                    id = preset.filters.size + 1,
                    enabled = true,
                    type = FilterType.GRAPHIC_EQ,
                    graphicPoints = points
                )
                preset.filters.add(graphicFilter)
            }
        }
    }
}
