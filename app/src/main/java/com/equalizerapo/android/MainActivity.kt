package com.equalizerapo.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.equalizerapo.android.model.ApoPreset
import com.equalizerapo.android.model.EqFilter
import com.equalizerapo.android.model.FilterType
import com.equalizerapo.android.model.PresetsDatabase
import com.equalizerapo.android.parser.EqualizerApoParser
import com.equalizerapo.android.service.AudioEffectService
import com.equalizerapo.android.ui.EqVisualizerView
import com.equalizerapo.android.ui.FilterListAdapter
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var visualizerView: EqVisualizerView
    private lateinit var recyclerFilters: RecyclerView
    private lateinit var filterAdapter: FilterListAdapter
    private lateinit var switchMasterPower: SwitchMaterial
    private lateinit var seekPreamp: SeekBar
    private lateinit var textPreampVal: TextView
    private lateinit var spinnerPresets: Spinner

    private var audioService: AudioEffectService? = null
    private var isServiceBound = false

    private val currentPreset = ApoPreset(
        name = PresetsDatabase.presets[0].name,
        preampDb = PresetsDatabase.presets[0].preampDb,
        filters = PresetsDatabase.presets[0].filters.map { it.copy() }.toMutableList()
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

        checkPermissions()
        initViews()
        startAudioService()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun initViews() {
        visualizerView = findViewById(R.id.visualizer_view)
        recyclerFilters = findViewById(R.id.recycler_filters)
        switchMasterPower = findViewById(R.id.switch_master_power)
        val switchMicPower = findViewById<SwitchMaterial>(R.id.switch_mic_power)
        seekPreamp = findViewById(R.id.seek_preamp)
        textPreampVal = findViewById(R.id.text_preamp_val)
        spinnerPresets = findViewById(R.id.spinner_presets)

        visualizerView.setPreset(currentPreset)

        // Horizontal LayoutManager for Peace GUI Vertical Band Cards
        recyclerFilters.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        filterAdapter = FilterListAdapter(currentPreset.filters) {
            onPresetUpdated()
        }
        recyclerFilters.adapter = filterAdapter

        // Preamp setup (-30 dB to +30 dB, max 600, 300 = 0 dB)
        val initialProgress = ((currentPreset.preampDb + 30f) * 10f).toInt().coerceIn(0, 600)
        seekPreamp.progress = initialProgress
        textPreampVal.text = "${String.format("%.1f", currentPreset.preampDb)} dB"

        seekPreamp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val db = (progress / 10f) - 30f
                    currentPreset.preampDb = db
                    textPreampVal.text = "${String.format("%.1f", db)} dB"
                    onPresetUpdated()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchMasterPower.setOnCheckedChangeListener { _, isChecked ->
            switchMasterPower.text = if (isChecked) "ON" else "OFF"
            audioService?.updatePreset(currentPreset, isChecked)
            Toast.makeText(this, if (isChecked) "Peace Equalizer APO Enabled" else "Peace Equalizer APO Disabled", Toast.LENGTH_SHORT).show()
        }
        
        switchMicPower.setOnCheckedChangeListener { _, isChecked ->
            audioService?.setMicLoopbackEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Mic Output Enabled" else "Mic Output Disabled", Toast.LENGTH_SHORT).show()
        }

        // Setup Presets Spinner
        val presetNames = PresetsDatabase.presets.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presetNames)
        spinnerPresets.adapter = adapter

        spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = PresetsDatabase.presets[position]
                loadPreset(selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btn_add_filter).setOnClickListener {
            val newId = currentPreset.filters.size + 1
            currentPreset.filters.add(EqFilter(newId, true, FilterType.PEAKING, 1000f, 0f, 1.41f))
            filterAdapter.notifyItemInserted(currentPreset.filters.size - 1)
            recyclerFilters.scrollToPosition(currentPreset.filters.size - 1)
            onPresetUpdated()
        }

        findViewById<Button>(R.id.btn_remove_filter).setOnClickListener {
            if (currentPreset.filters.isNotEmpty()) {
                val lastIdx = currentPreset.filters.size - 1
                currentPreset.filters.removeAt(lastIdx)
                filterAdapter.notifyItemRemoved(lastIdx)
                onPresetUpdated()
            }
        }

        findViewById<Button>(R.id.btn_autoeq).setOnClickListener {
            showAutoEqDialog()
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
    }

    private fun loadPreset(preset: ApoPreset) {
        currentPreset.preampDb = preset.preampDb
        currentPreset.filters.clear()
        currentPreset.filters.addAll(preset.filters.map { it.copy() })

        filterAdapter.notifyDataSetChanged()
        val preampProgress = ((currentPreset.preampDb + 30f) * 10f).toInt().coerceIn(0, 600)
        seekPreamp.progress = preampProgress
        textPreampVal.text = "${String.format("%.1f", currentPreset.preampDb)} dB"
        onPresetUpdated()
    }

    private fun showAutoEqDialog() {
        val autoEqPresets = PresetsDatabase.presets.filter { it.name.startsWith("AutoEQ") }
        val names = autoEqPresets.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select AutoEQ Headphone Calibration")
            .setItems(names) { _, which ->
                loadPreset(autoEqPresets[which])
                Toast.makeText(this, "Loaded ${names[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioEffectService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                val preampProgress = ((currentPreset.preampDb + 30f) * 10f).toInt().coerceIn(0, 600)
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
