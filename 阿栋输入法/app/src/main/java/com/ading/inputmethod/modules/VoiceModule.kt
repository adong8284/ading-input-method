package com.ading.inputmethod.modules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音输入模块
 * 集成系统SpeechRecognizer，支持在线语音识别和离线备用方案
 */
class VoiceModule(context: Context) : BaseKeyboardModule(context) {
    
    companion object {
        private const val TAG = "VoiceModule"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        
        // 权限请求码
        const val REQUEST_RECORD_AUDIO_PERMISSION = 1001
    }
    
    // 语音识别器
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = AtomicBoolean(false)
    
    // 音频录制（用于波形显示）
    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    
    // 回调
    private var recognitionCallback: ((String?) -> Unit)? = null
    private var waveformCallback: ((FloatArray) -> Unit)? = null
    
    // 波形数据
    private val waveformData = FloatArray(100)
    private var waveformIndex = 0
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 识别结果缓存
    private val recognitionCache = mutableListOf<String>()
    private val maxCacheSize = 50
    
    override fun getDescription(): String {
        return "语音输入模块，支持系统级语音识别和实时波形显示"
    }
    
    override fun onInitialize() {
        // 检查语音识别服务是否可用
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition service not available")
            return
        }
        
        // 创建语音识别器
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
        
        Log.d(TAG, "Voice module initialized")
    }
    
    override fun onRelease() {
        // 停止识别
        stopRecognition()
        
        // 停止录音
        stopRecording()
        
        // 释放资源
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        audioRecord?.release()
        audioRecord = null
        
        // 清理回调
        recognitionCallback = null
        waveformCallback = null
        
        Log.d(TAG, "Voice module released")
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 语音模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    /**
     * 开始语音识别
     */
    fun startRecognition(callback: (String?) -> Unit) {
        if (!enabled) {
            callback.invoke(null)
            return
        }
        
        if (isListening.get()) {
            Log.w(TAG, "Already listening")
            return
        }
        
        // 检查权限
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "No record audio permission")
            callback.invoke(null)
            return
        }
        
        // 设置回调
        recognitionCallback = callback
        
        // 开始录音（用于波形显示）
        startRecording()
        
        // 开始语音识别
        startSpeechRecognition()
        
        Log.d(TAG, "Voice recognition started")
    }
    
    /**
     * 停止语音识别
     */
    fun stopRecognition() {
        if (!isListening.get()) {
            return
        }
        
        // 停止语音识别
        speechRecognizer?.stopListening()
        isListening.set(false)
        
        // 停止录音
        stopRecording()
        
        Log.d(TAG, "Voice recognition stopped")
    }
    
    /**
     * 检查是否有录音权限
     */
    fun hasRecordAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * 设置波形回调
     */
    fun setWaveformCallback(callback: (FloatArray) -> Unit) {
        waveformCallback = callback
    }
    
    /**
     * 获取最近的识别结果
     */
    fun getRecentResults(limit: Int = 10): List<String> {
        return recognitionCache.take(limit)
    }
    
    /**
     * 清除识别缓存
     */
    fun clearRecognitionCache() {
        recognitionCache.clear()
    }
    
    /**
     * 检查语音识别是否可用
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) && hasRecordAudioPermission()
    }
    
    /**
     * 获取识别状态
     */
    fun getRecognitionState(): String {
        return when {
            !isRecognitionAvailable() -> "UNAVAILABLE"
            isListening.get() -> "LISTENING"
            else -> "READY"
        }
    }
    
    /**
     * 创建识别监听器
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d(TAG, "Ready for speech")
                isListening.set(true)
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // 更新波形数据
                updateWaveform(rmsdB)
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // 不需要处理
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                isListening.set(false)
            }
            
            override fun onError(error: Int) {
                Log.e(TAG, "Recognition error: $error")
                isListening.set(false)
                stopRecording()
                
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "无匹配结果"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    else -> "未知错误"
                }
                
                recognitionCallback?.invoke("错误: $errorMessage")
                recognitionCallback = null
            }
            
            override fun onResults(results: android.os.Bundle?) {
                Log.d(TAG, "Recognition results received")
                isListening.set(false)
                stopRecording()
                
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestMatch = matches?.firstOrNull()
                
                if (bestMatch != null) {
                    // 添加到缓存
                    addToRecognitionCache(bestMatch)
                    
                    // 回调结果
                    recognitionCallback?.invoke(bestMatch)
                } else {
                    recognitionCallback?.invoke(null)
                }
                
                recognitionCallback = null
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()
                
                if (partialText != null) {
                    Log.d(TAG, "Partial result: $partialText")
                    // 可以在这里实时显示部分结果
                }
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                Log.d(TAG, "Recognition event: $eventType")
            }
        }
    }
    
    /**
     * 开始语音识别
     */
    private fun startSpeechRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN") // 中文普通话
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
            }
            
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            recognitionCallback?.invoke("启动语音识别失败: ${e.message}")
            recognitionCallback = null
        }
    }
    
    /**
     * 开始录音（用于波形显示）
     */
    private fun startRecording() {
        if (isRecording.get()) {
            return
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE
            )
            
            audioRecord?.startRecording()
            isRecording.set(true)
            
            // 开始波形数据采集线程
            startWaveformThread()
            
            Log.d(TAG, "Audio recording started for waveform")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
        }
    }
    
    /**
     * 停止录音
     */
    private fun stopRecording() {
        if (!isRecording.get()) {
            return
        }
        
        isRecording.set(false)
        
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        
        Log.d(TAG, "Audio recording stopped")
    }
    
    /**
     * 开始波形数据采集线程
     */
    private fun startWaveformThread() {
        Thread {
            val buffer = ShortArray(BUFFER_SIZE / 2)
            
            while (isRecording.get() && audioRecord != null) {
                try {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // 计算RMS值用于波形
                        val rms = calculateRMS(buffer, bytesRead)
                        
                        // 更新波形数据
                        updateWaveformData(rms)
                        
                        // 回调波形数据
                        waveformCallback?.let { callback ->
                            val currentWaveform = waveformData.copyOf()
                            mainHandler.post {
                                callback.invoke(currentWaveform)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading audio data", e)
                    break
                }
            }
        }.start()
    }
    
    /**
     * 计算RMS值
     */
    private fun calculateRMS(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE.toDouble()
            sum += sample * sample
        }
        
        val rms = Math.sqrt(sum / length).toFloat()
        return rms
    }
    
    /**
     * 更新波形数据
     */
    private fun updateWaveformData(rms: Float) {
        waveformData[waveformIndex] = rms
        waveformIndex = (waveformIndex + 1) % waveformData.size
    }
    
    /**
     * 更新波形显示
     */
    private fun updateWaveform(rmsdB: Float) {
        // 将dB值转换为0-1范围
        val normalizedValue = (rmsdB + 20) / 60 // 假设-20到40dB范围
        val clampedValue = normalizedValue.coerceIn(0f, 1f)
        
        updateWaveformData(clampedValue)
        
        // 回调波形数据
        waveformCallback?.let { callback ->
            val currentWaveform = waveformData.copyOf()
            mainHandler.post {
                callback.invoke(currentWaveform)
            }
        }
    }
    
    /**
     * 添加到识别缓存
     */
    private fun addToRecognitionCache(text: String) {
        recognitionCache.add(0, text)
        
        // 限制缓存大小
        if (recognitionCache.size > maxCacheSize) {
            recognitionCache.removeAt(recognitionCache.size - 1)
        }
    }
    
    /**
     * 获取波形数据
     */
    fun getWaveformData(): FloatArray {
        return waveformData.copyOf()
    }
    
    /**
     * 重置波形数据
     */
    fun resetWaveformData() {
        waveformData.fill(0f)
        waveformIndex = 0
    }
    
    /**
     * 设置识别语言
     */
    fun setLanguage(language: String) {
        // 这里可以扩展支持多语言
        // 目前只支持中文普通话
    }
    
    /**
     * 获取支持的语言列表
     */
    fun getSupportedLanguages(): List<String> {
        return listOf("zh-CN", "en-US", "ja-JP", "ko-KR")
    }
    
    /**
     * 检查是否正在识别
     */
    fun isRecognizing(): Boolean {
        return isListening.get()
    }
    
    /**
     * 取消当前识别
     */
    fun cancelRecognition() {
        speechRecognizer?.cancel()
        isListening.set(false)
        stopRecording()
        recognitionCallback = null
    }
    
    /**
     * 获取识别缓存大小
     */
    fun getCacheSize(): Int {
        return recognitionCache.size
    }
    
    /**
     * 导出识别历史
     */
    fun exportRecognitionHistory(): String {
        return recognitionCache.joinToString("\n")
    }
    
    /**
     * 导入识别历史
     */
    fun importRecognitionHistory(history: String) {
        recognitionCache.clear()
        recognitionCache.addAll(history.lines().filter { it.isNotBlank() })
        
        // 限制大小
        if (recognitionCache.size > maxCacheSize) {
            recognitionCache.subList(maxCacheSize, recognitionCache.size).clear()
        }
    }
    
    /**
     * 获取模块统计信息
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "enabled" to enabled,
            "initialized" to initialized,
            "listening" to isListening.get(),
            "recording" to isRecording.get(),
            "cache_size" to recognitionCache.size,
            "waveform_data_points" to waveformData.size,
            "recognition_available" to isRecognitionAvailable(),
            "last_used" to lastUsedTime
        )
    }
}