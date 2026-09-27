package com.equalizerapo.android.model

/**
 * Built-in presets database including Peace GUI factory presets and AutoEQ headphone profiles.
 */
object PresetsDatabase {

    val presets: List<ApoPreset> = listOf(
        ApoPreset(
            name = "Flat (Default)",
            preampDb = 0f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.PEAKING, 31f, 0f, 1.41f),
                EqFilter(2, true, FilterType.PEAKING, 63f, 0f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 125f, 0f, 1.41f),
                EqFilter(4, true, FilterType.PEAKING, 250f, 0f, 1.41f),
                EqFilter(5, true, FilterType.PEAKING, 500f, 0f, 1.41f),
                EqFilter(6, true, FilterType.PEAKING, 1000f, 0f, 1.41f),
                EqFilter(7, true, FilterType.PEAKING, 2000f, 0f, 1.41f),
                EqFilter(8, true, FilterType.PEAKING, 4000f, 0f, 1.41f),
                EqFilter(9, true, FilterType.PEAKING, 8000f, 0f, 1.41f),
                EqFilter(10, true, FilterType.PEAKING, 16000f, 0f, 1.41f)
            )
        ),
        ApoPreset(
            name = "Peace Bass Boost",
            preampDb = -3.5f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.LOW_SHELF, 80f, 6.5f, 0.71f),
                EqFilter(2, true, FilterType.PEAKING, 125f, 4.0f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 250f, 2.0f, 1.41f),
                EqFilter(4, true, FilterType.PEAKING, 1000f, 0f, 1.41f),
                EqFilter(5, true, FilterType.PEAKING, 4000f, 1.0f, 1.41f),
                EqFilter(6, true, FilterType.HIGH_SHELF, 10000f, 2.0f, 0.71f)
            )
        ),
        ApoPreset(
            name = "Peace Treble Boost",
            preampDb = -2.5f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.PEAKING, 60f, -1.0f, 1.41f),
                EqFilter(2, true, FilterType.PEAKING, 1000f, 1.0f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 3000f, 3.5f, 1.41f),
                EqFilter(4, true, FilterType.HIGH_SHELF, 8000f, 6.0f, 0.71f)
            )
        ),
        ApoPreset(
            name = "Peace Rock & Metal",
            preampDb = -4.0f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.LOW_SHELF, 60f, 5.0f, 0.71f),
                EqFilter(2, true, FilterType.PEAKING, 150f, 3.0f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 500f, -2.0f, 1.41f),
                EqFilter(4, true, FilterType.PEAKING, 2500f, 3.5f, 1.41f),
                EqFilter(5, true, FilterType.HIGH_SHELF, 8000f, 4.5f, 0.71f)
            )
        ),
        ApoPreset(
            name = "AutoEQ - Sony WH-1000XM4/XM5",
            preampDb = -4.8f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.LOW_SHELF, 105f, -3.5f, 0.71f),
                EqFilter(2, true, FilterType.PEAKING, 220f, -2.0f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 1200f, 2.5f, 1.41f),
                EqFilter(4, true, FilterType.PEAKING, 3100f, 4.0f, 1.41f),
                EqFilter(5, true, FilterType.HIGH_SHELF, 8000f, 2.0f, 0.71f)
            )
        ),
        ApoPreset(
            name = "AutoEQ - Sennheiser HD 600 / HD 650",
            preampDb = -5.5f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.LOW_SHELF, 80f, 5.5f, 0.71f),
                EqFilter(2, true, FilterType.PEAKING, 200f, -1.0f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 3000f, -2.5f, 2.00f),
                EqFilter(4, true, FilterType.HIGH_SHELF, 10000f, 2.0f, 0.71f)
            )
        ),
        ApoPreset(
            name = "AutoEQ - Apple AirPods Pro 2",
            preampDb = -3.0f,
            filters = mutableListOf(
                EqFilter(1, true, FilterType.LOW_SHELF, 70f, 2.5f, 0.71f),
                EqFilter(2, true, FilterType.PEAKING, 1500f, -1.5f, 1.41f),
                EqFilter(3, true, FilterType.PEAKING, 4800f, 3.0f, 2.00f),
                EqFilter(4, true, FilterType.HIGH_SHELF, 9000f, 1.5f, 0.71f)
            )
        )
    )
}
