package com.equalizerapo.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import com.equalizerapo.android.service.AudioEffectService

class AudioSessionReceiver : BroadcastReceiver() {

    private val TAG = "AudioSessionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "Unknown"

        if (sessionId == AudioEffect.ERROR_BAD_VALUE) return

        Log.d(TAG, "Received Audio Session event: $action | Session ID: $sessionId | Package: $packageName")

        val serviceIntent = Intent(context, AudioEffectService::class.java).apply {
            putExtra("ACTION_TYPE", action)
            putExtra("SESSION_ID", sessionId)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
