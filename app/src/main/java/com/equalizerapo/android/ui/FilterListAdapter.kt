package com.equalizerapo.android.ui

import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.equalizerapo.android.R
import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType

class FilterListAdapter(
    private val filters: MutableList<EqFilter>,
    private val onFilterChanged: () -> Unit
) : RecyclerView.Adapter<FilterListAdapter.FilterViewHolder>() {

    class FilterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkEnabled: CheckBox = view.findViewById(R.id.check_enabled)
        val textBandNum: TextView = view.findViewById(R.id.text_band_num)
        val btnFilterType: Button = view.findViewById(R.id.btn_filter_type)
        val editFreq: EditText = view.findViewById(R.id.edit_freq)
        val textGainVal: TextView = view.findViewById(R.id.text_gain_val)
        val seekGainVert: SeekBar = view.findViewById(R.id.seek_gain_vert)
        val editQ: EditText = view.findViewById(R.id.edit_q)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter_vertical, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        
        holder.checkEnabled.isChecked = filter.enabled
        holder.textBandNum.text = "b${position + 1}"
        holder.btnFilterType.text = filter.type.code

        if (!holder.editFreq.hasFocus()) {
            holder.editFreq.setText(filter.frequency.toInt().toString())
        }
        if (!holder.editQ.hasFocus()) {
            holder.editQ.setText(String.format("%.2f", filter.qFactor))
        }

        // SeekBar maps -30.0 dB to +30.0 dB (max 600, progress 300 = 0 dB)
        val progress = ((filter.gain + 30f) * 10f).toInt().coerceIn(0, 600)
        holder.seekGainVert.progress = progress
        holder.textGainVal.text = "${String.format("%.1f", filter.gain)} dB"

        holder.checkEnabled.setOnCheckedChangeListener { _, isChecked ->
            filter.enabled = isChecked
            onFilterChanged()
        }

        holder.btnFilterType.setOnClickListener { view ->
            val types = FilterType.values().map { "${it.code} (${it.displayName})" }.toTypedArray()
            AlertDialog.Builder(view.context)
                .setTitle("Select Filter Type for Band ${position + 1}")
                .setItems(types) { _, which ->
                    val selectedType = FilterType.values()[which]
                    filter.type = selectedType
                    holder.btnFilterType.text = selectedType.code
                    onFilterChanged()
                }
                .show()
        }

        holder.editFreq.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val freq = s?.toString()?.toFloatOrNull()
                if (freq != null && freq > 0) {
                    filter.frequency = freq
                    onFilterChanged()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        holder.editQ.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.toFloatOrNull()
                if (q != null && q > 0) {
                    filter.qFactor = q
                    onFilterChanged()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        holder.seekGainVert.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gain = (progress / 10f) - 30f
                    filter.gain = gain
                    holder.textGainVal.text = "${String.format("%.1f", gain)} dB"
                    onFilterChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun getItemCount(): Int = filters.size
}
