package com.equalizerapo.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.equalizerapo.android.R
import com.equalizerapo.android.model.EqFilter

class FilterListAdapter(
    private val filters: MutableList<EqFilter>,
    private val onFilterChanged: () -> Unit
) : RecyclerView.Adapter<FilterListAdapter.FilterViewHolder>() {

    class FilterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkEnabled: CheckBox = view.findViewById(R.id.check_enabled)
        val textTitle: TextView = view.findViewById(R.id.text_filter_title)
        val textDetails: TextView = view.findViewById(R.id.text_filter_details)
        val seekGain: SeekBar = view.findViewById(R.id.seek_gain)
        val textGainValue: TextView = view.findViewById(R.id.text_gain_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        holder.checkEnabled.isChecked = filter.enabled
        holder.textTitle.text = "Filter ${position + 1}: ${filter.type.displayName}"
        holder.textDetails.text = "Fc: ${filter.frequency.toInt()} Hz | Q: ${filter.qFactor}"
        
        // Seekbar maps -12.0 dB to +12.0 dB (range 0..240, progress 120 = 0 dB)
        val progress = ((filter.gain + 12f) * 10f).toInt().coerceIn(0, 240)
        holder.seekGain.progress = progress
        holder.textGainValue.text = "${String.format("%.1f", filter.gain)} dB"

        holder.checkEnabled.setOnCheckedChangeListener { _, isChecked ->
            filter.enabled = isChecked
            onFilterChanged()
        }

        holder.seekGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gain = (progress / 10f) - 12f
                    filter.gain = gain
                    holder.textGainValue.text = "${String.format("%.1f", gain)} dB"
                    onFilterChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun getItemCount(): Int = filters.size
}
