package com.equalizerapo.android

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.equalizerapo.android.model.ApoPreset
import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType
import com.equalizerapo.android.parser.EqualizerApoParser
import com.equalizerapo.android.service.AudioEffectService
import com.equalizerapo.android.ui.EqVisualizerView
import com.equalizerapo.android.ui.FilterListAdapter
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var visualizerView: EqVisualizerView
    private lateinit var recyclerFilters: RecyclerView
    private lateinit var filterAdapter: FilterListAdapter
    private lateinit var switchMasterPower: SwitchMaterial
    private lateinit var seekPreamp: SeekBar
    private lateinit var textPreampVal: TextView

    private var audioService: AudioEffectService? = null
    private var isServiceBound = false

    private val currentPreset = ApoPreset(
        name = "Default Preset",
        preampDb = -2.0f,
        filters = mutableListOf(
            EqFilter(1, true, FilterType.PEAKING, 105f, 3.5f, 1.41f),
            EqFilter(2, true, FilterType.PEAKING, 300f, -1.5f, 1.00f),
            EqFilter(3, true, FilterType.PEAKING, 2400f, 2.0f, 1.41f),
            EqFilter(4, true, FilterType.PEAKING, 8000f, 4.0f, 1.41f),
            EqFilter(5, true, FilterType.HIGH_SHELF, 12000f, -1.0f, 0.71f)
        )
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioEffectService.LocalBinder
            audioService = binder.getService()
            isServiceBound = true
            updateDsp()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
        }
    }

    private val importConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importApoConfigFile(uri)
            }
        }
    }

    private val exportConfigLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { exportApoConfigFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        startAudioService()
    }

    private fun initViews() {
        visualizerView = findViewById(R.id.visualizer_view)
        recyclerFilters = findViewById(R.id.recycler_filters)
        switchMasterPower = findViewById(R.id.switch_master_power)
        seekPreamp = findViewById(R.id.seek_preamp)
        textPreampVal = findViewById(R.id.text_preamp_val)

        visualizerView.setPreset(currentPreset)

        recyclerFilters.layoutManager = LinearLayoutManager(this)
        filterAdapter = FilterListAdapter(currentPreset.filters) {
            onPresetUpdated()
        }
        recyclerFilters.adapter = filterAdapter

        // Preamp setup (-12 dB to +12 dB)
        val initialProgress = ((currentPreset.preampDb + 12f) * 10f).toInt().coerceIn(0, 240)
        seekPreamp.progress = initialProgress
        textPreampVal.text = "${String.format("%.1f", currentPreset.preampDb)} dB"

        seekPreamp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val db = (progress / 10f) - 12f
                    currentPreset.preampDb = db
                    textPreampVal.text = "${String.format("%.1f", db)} dB"
                    onPresetUpdated()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchMasterPower.setOnCheckedChangeListener { _, isChecked ->
            audioService?.updatePreset(currentPreset, isChecked)
            Toast.makeText(this, if (isChecked) "Equalizer APO Enabled" else "Equalizer APO Disabled", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_import_config).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importConfigLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btn_export_config).setOnClickListener {
            exportConfigLauncher.launch("config.txt")
        }

        findViewById<Button>(R.id.btn_add_filter).setOnClickListener {
            val newId = currentPreset.filters.size + 1
            currentPreset.filters.add(EqFilter(newId, true, FilterType.PEAKING, 1000f, 0f, 1.41f))
            filterAdapter.notifyItemInserted(currentPreset.filters.size - 1)
            onPresetUpdated()
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioEffectService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun onPresetUpdated() {
        visualizerView.setPreset(currentPreset)
        updateDsp()
    }

    private fun updateDsp() {
        if (switchMasterPower.isChecked) {
            audioService?.updatePreset(currentPreset, true)
        }
    }

    private fun importApoConfigFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val imported = EqualizerApoParser.parse("Imported Preset", stream)
                currentPreset.preampDb = imported.preampDb
                currentPreset.filters.clear()
                currentPreset.filters.addAll(imported.filters)

                filterAdapter.notifyDataSetChanged()
                val preampProgress = ((currentPreset.preampDb + 12f) * 10f).toInt().coerceIn(0, 240)
                seekPreamp.progress = preampProgress
                textPreampVal.text = "${String.format("%.1f", currentPreset.preampDb)} dB"
                onPresetUpdated()
                Toast.makeText(this, "Imported ${imported.filters.size} bands from config.txt", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to parse APO file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportApoConfigFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(currentPreset.exportToText().toByteArray())
                Toast.makeText(this, "Exported config.txt successfully!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export config: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onDestroy()
    }
}
