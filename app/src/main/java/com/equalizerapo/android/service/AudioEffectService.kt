package com.equalizerapo.android.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.equalizerapo.android.MainActivity
import com.equalizerapo.android.R
import com.equalizerapo.android.dsp.AudioDspEngine
import com.equalizerapo.android.model.ApoPreset

class AudioEffectService : Service() {

    private val binder = LocalBinder()
    lateinit var dspEngine: AudioDspEngine
    private val CHANNEL_ID = "EqualizerAPO_PeaceChannel"
    private val NOTIFICATION_ID = 1001

    private var currentPreset: ApoPreset? = null
    private var isMasterEnabled = true

    inner class LocalBinder : Binder() {
        fun getService(): AudioEffectService = this@AudioEffectService
    }

    override fun onCreate() {
        super.onCreate()
        dspEngine = AudioDspEngine(this)
        dspEngine.attachSession(0) // System-wide global audio output session 0
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Peace Equalizer APO is active (System-wide DSP)")
        startForeground(NOTIFICATION_ID, notification)

        intent?.let {
            val actionType = it.getStringExtra("ACTION_TYPE")
            val sessionId = it.getIntExtra("SESSION_ID", -1)

            if (sessionId > 0 && actionType != null) {
                if (actionType == "android.media.action.OPEN_AUDIO_EFFECT_SESSION") {
                    dspEngine.attachSession(sessionId)
                } else if (actionType == "android.media.action.CLOSE_AUDIO_EFFECT_SESSION") {
                    dspEngine.detachSession(sessionId)
                }
            }
        }

        return START_STICKY
    }

    fun updatePreset(preset: ApoPreset, enabled: Boolean) {
        this.currentPreset = preset
        this.isMasterEnabled = enabled
        dspEngine.setEnabled(enabled)
        if (enabled) {
            dspEngine.applyPreset(preset)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Peace Equalizer APO")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_power)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Peace Equalizer APO Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System-wide Audio Equalization Engine"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        dspEngine.release()
        super.onDestroy()
    }
}
