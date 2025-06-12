package com.whispercppdemo.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.whispercppdemo.media.encodeWaveFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class Recorder {
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var recorder: AudioRecordThread? = null

    suspend fun startRecording(
        outputFile: File,
        onError: (Exception) -> Unit,
        onChunkAvailable: (ShortArray) -> Unit
    ) = withContext(scope.coroutineContext) {
        recorder = AudioRecordThread(outputFile, onError, onChunkAvailable)
        recorder?.start()
    }

    suspend fun stopRecording() = withContext(scope.coroutineContext) {
        recorder?.stopRecording()
        @Suppress("BlockingMethodInNonBlockingContext")
        recorder?.join()
        recorder = null
    }
}

private class AudioRecordThread(
    private val outputFile: File,
    private val onError: (Exception) -> Unit,
    private val onChunkAvailable: (ShortArray) -> Unit
) : Thread("AudioRecorder") {
    private var quit = AtomicBoolean(false)
    private val sampleRate = 16000
    private val chunkDurationMs = 4000 // 4.5 seconds (to match transcription time)
    private val samplesPerChunk = (sampleRate * chunkDurationMs) / 1000
    private val maxBufferDurationMs = 6000 // N seconds max buffer (1.5 chunk lengths)
    private val maxBufferSamples = (sampleRate * maxBufferDurationMs) / 1000
    
    companion object {
        private const val TAG = "AudioRecorder"
    }

    @SuppressLint("MissingPermission")
    override fun run() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4
            val buffer = ShortArray(bufferSize / 2)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            try {
                audioRecord.startRecording()

                // Circular buffer for all data with maximum capacity
                val circularBuffer = ShortArray(maxBufferSamples)
                var writeIndex = 0
                var totalSamples = 0
                var lastOverflowLogTime = 0L
                val overflowLogIntervalMs = 5000L // Log overflow every 5 seconds
                val chunkBuffer = mutableListOf<Short>()

                while (!quit.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            val sample = buffer[i]
                            
                            // Check if we're about to start overwriting (buffer is full)
                            if (totalSamples >= maxBufferSamples) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastOverflowLogTime >= overflowLogIntervalMs) {
                                    Log.w(TAG, "Audio buffer full (${maxBufferDurationMs}ms), overwriting old data. System is falling behind.")
                                    lastOverflowLogTime = currentTime
                                }
                            }
                            
                            // Add to circular buffer
                            circularBuffer[writeIndex] = sample
                            writeIndex = (writeIndex + 1) % maxBufferSamples
                            totalSamples++
                            
                            chunkBuffer.add(sample)

                            // If we have enough samples for a chunk, process it
                            if (chunkBuffer.size >= samplesPerChunk) {
                                onChunkAvailable(chunkBuffer.toShortArray())
                                chunkBuffer.clear()
                            }
                        }
                    } else {
                        throw java.lang.RuntimeException("audioRecord.read returned $read")
                    }
                }

                // Process any remaining samples in the last chunk
                if (chunkBuffer.isNotEmpty()) {
                    onChunkAvailable(chunkBuffer.toShortArray())
                }

                audioRecord.stop()
                
                // Log final statistics
                val samplesLost = if (totalSamples > maxBufferSamples) totalSamples - maxBufferSamples else 0
                val secondsLost = samplesLost.toFloat() / sampleRate
                if (samplesLost > 0) {
                    Log.i(TAG, "Recording completed. Lost ${samplesLost} samples (${String.format("%.2f", secondsLost)}s) due to buffer overflow.")
                } else {
                    Log.i(TAG, "Recording completed. No samples lost.")
                }
                
                // Extract data from circular buffer for final encoding
                val finalData = if (totalSamples <= maxBufferSamples) {
                    // Buffer never wrapped, use data from start to writeIndex
                    circularBuffer.sliceArray(0 until writeIndex)
                } else {
                    // Buffer wrapped, reconstruct in correct order
                    val result = ShortArray(maxBufferSamples)
                    for (i in 0 until maxBufferSamples) {
                        val readIndex = (writeIndex + i) % maxBufferSamples
                        result[i] = circularBuffer[readIndex]
                    }
                    result
                }
                
                encodeWaveFile(outputFile, finalData)
            } finally {
                audioRecord.release()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun stopRecording() {
        quit.set(true)
    }
}