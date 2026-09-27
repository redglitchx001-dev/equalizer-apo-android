package com.equalizerapo.android.dsp

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log

class MicLoopbackEngine {
    private val TAG = "MicLoopbackEngine"
    private var isLoopbackActive = false
    private var loopbackThread: Thread? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    var onSessionStarted: ((Int) -> Unit)? = null
    var onSessionEnded: ((Int) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startLoopback() {
        if (isLoopbackActive) return

        val sampleRate = 44100
        val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
        val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufSizeIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
        val minBufSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

        val bufferSize = Math.max(minBufSizeIn, minBufSizeOut) * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioFormat,
                bufferSize
            )

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfigOut,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED || 
                audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord or AudioTrack")
                stopLoopback()
                return
            }

            val sessionId = audioTrack!!.audioSessionId
            onSessionStarted?.invoke(sessionId)

            isLoopbackActive = true
            loopbackThread = Thread {
                val buffer = ByteArray(bufferSize)
                audioRecord?.startRecording()
                audioTrack?.play()

                while (isLoopbackActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        audioTrack?.write(buffer, 0, read)
                    }
                }
                
                audioRecord?.stop()
                audioTrack?.stop()
                audioRecord?.release()
                audioTrack?.release()
                
                onSessionEnded?.invoke(sessionId)
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting loopback: ${e.message}")
            stopLoopback()
        }
    }

    fun stopLoopback() {
        isLoopbackActive = false
        loopbackThread?.join(500)
        loopbackThread = null
    }
}
