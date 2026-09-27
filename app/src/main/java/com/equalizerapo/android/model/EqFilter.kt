package com.equalizerapo.android.model

/**
 * Filter types supported by Equalizer APO and mapped to Android DSP engine.
 */
enum class FilterType(val code: String, val displayName: String) {
    PREAMP("Preamp", "Preamp Gain"),
    PEAKING("PK", "Peaking Filter"),
    LOW_SHELF("LS", "Low Shelf Filter"),
    HIGH_SHELF("HS", "High Shelf Filter"),
    LOW_PASS("LP", "Low Pass Filter"),
    HIGH_PASS("HP", "High Pass Filter"),
    BAND_PASS("BP", "Band Pass Filter"),
    NOTCH("NO", "Notch Filter"),
    GRAPHIC_EQ("GraphicEQ", "Graphic EQ");

    companion object {
        fun fromCode(code: String): FilterType {
            return values().find { it.code.equals(code, ignoreCase = true) } ?: PEAKING
        }
    }
}

/**
 * Individual filter band representation matching Equalizer APO config specification.
 */
data class EqFilter(
    val id: Int,
    var enabled: Boolean = true,
    var type: FilterType = FilterType.PEAKING,
    var frequency: Float = 1000f, // Hz
    var gain: Float = 0f,         // dB
    var qFactor: Float = 1.414f,  // Q value
    var graphicPoints: MutableList<Pair<Float, Float>> = mutableListOf() // Frequency to Gain pairs for GraphicEQ
) {
    /**
     * Format into official Equalizer APO syntax string.
     * Example: "Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.41"
     */
    fun toApoSyntax(): String {
        return when (type) {
            FilterType.PREAMP -> "Preamp: ${if (gain >= 0) "+" else ""}${String.format("%.2f", gain)} dB"
            FilterType.GRAPHIC_EQ -> {
                val pointsStr = graphicPoints.joinToString("; ") { "${it.first.toInt()} ${it.second}" }
                "GraphicEQ: $pointsStr"
            }
            else -> {
                val status = if (enabled) "ON" else "OFF"
                "Filter $id: $status ${type.code} Fc ${frequency.toInt()} Hz Gain ${String.format("%.2f", gain)} dB Q ${String.format("%.2f", qFactor)}"
            }
        }
    }
}
