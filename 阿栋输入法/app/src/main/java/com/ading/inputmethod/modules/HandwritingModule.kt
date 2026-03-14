package com.ading.inputmethod.modules

import android.content.Context
import android.gesture.Gesture
import android.gesture.GestureOverlayView
import android.gesture.GestureStroke
import android.gesture.GesturePoint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 手写输入模块
 * 支持中文、英文、数字手写识别，使用ML Kit Digital Ink Recognition
 */
class HandwritingModule(context: Context) : BaseKeyboardModule(context) {
    
    companion object {
        private const val TAG = "HandwritingModule"
        private const val STROKE_WIDTH = 8f
        private const val INK_COLOR = Color.BLACK
        private const val BACKGROUND_COLOR = Color.WHITE
        private const val CANVAS_WIDTH = 800
        private const val CANVAS_HEIGHT = 400
        private const val RECOGNITION_TIMEOUT_MS = 3000L
    }
    
    // ML Kit手写识别
    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null
    
    // 当前墨水（笔画数据）
    private var currentInk: Ink? = null
    private var currentStroke: Ink.Stroke.Builder? = null
    
    // 识别结果回调
    private var recognitionCallback: ((String?) -> Unit)? = null
    
    // 笔画历史（用于回放）
    private val strokeHistory = mutableListOf<Pair<Long, List<PointF>>>()
    private val maxHistorySize = 100
    
    // 线程池
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 识别语言
    private var currentLanguage = "zh-Hans" // 简体中文
    
    // 识别统计
    private var recognitionCount = 0
    private var successCount = 0
    private var totalRecognitionTime = 0L
    
    override fun getDescription(): String {
        return "手写输入模块，支持中文、英文、数字手写识别，使用ML Kit Digital Ink Recognition"
    }
    
    override fun onInitialize() {
        // 初始化ML Kit手写识别
        initializeRecognitionModel()
        
        Log.d(TAG, "Handwriting module initialized with language: $currentLanguage")
    }
    
    override fun onRelease() {
        // 清理资源
        recognizer?.close()
        recognizer = null
        model = null
        currentInk = null
        currentStroke = null
        strokeHistory.clear()
        
        // 关闭线程池
        executorService.shutdown()
        
        Log.d(TAG, "Handwriting module released")
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 手写模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    /**
     * 开始新的笔画
     */
    fun startNewStroke(x: Float, y: Float, timestamp: Long) {
        // 结束当前笔画（如果有）
        finishCurrentStroke()
        
        // 开始新笔画
        currentStroke = Ink.Stroke.builder()
        currentStroke?.addPoint(Ink.Point.create(x, y, timestamp))
        
        // 记录笔画起点
        val points = mutableListOf(PointF(x, y))
        strokeHistory.add(Pair(timestamp, points))
        
        // 限制历史大小
        if (strokeHistory.size > maxHistorySize) {
            strokeHistory.removeAt(0)
        }
    }
    
    /**
     * 添加点到当前笔画
     */
    fun addPointToStroke(x: Float, y: Float, timestamp: Long) {
        currentStroke?.addPoint(Ink.Point.create(x, y, timestamp))
        
        // 更新笔画历史
        if (strokeHistory.isNotEmpty()) {
            val lastEntry = strokeHistory.last()
            val updatedPoints = lastEntry.second.toMutableList().apply {
                add(PointF(x, y))
            }
            strokeHistory[strokeHistory.size - 1] = Pair(lastEntry.first, updatedPoints)
        }
    }
    
    /**
     * 结束当前笔画
     */
    fun finishCurrentStroke() {
        currentStroke?.let { strokeBuilder ->
            val stroke = strokeBuilder.build()
            
            // 添加到当前墨水
            if (currentInk == null) {
                currentInk = Ink.builder().addStroke(stroke).build()
            } else {
                currentInk = currentInk?.toBuilder()?.addStroke(stroke)?.build()
            }
            
            currentStroke = null
        }
    }
    
    /**
     * 识别手写内容
     */
    fun recognize(callback: (String?) -> Unit) {
        if (!enabled || currentInk == null) {
            callback.invoke(null)
            return
        }
        
        recognitionCallback = callback
        recognitionCount++
        
        val startTime = System.currentTimeMillis()
        
        executorService.execute {
            try {
                val ink = currentInk ?: return@execute
                
                recognizer?.recognize(ink)?.addOnSuccessListener { result: RecognitionResult ->
                    val recognitionTime = System.currentTimeMillis() - startTime
                    totalRecognitionTime += recognitionTime
                    
                    val candidates = result.candidates
                    if (candidates.isNotEmpty()) {
                        val bestCandidate = candidates[0].text
                        successCount++
                        
                        Log.d(TAG, "Recognition successful: $bestCandidate (${recognitionTime}ms)")
                        
                        mainHandler.post {
                            recognitionCallback?.invoke(bestCandidate)
                            recognitionCallback = null
                        }
                    } else {
                        Log.w(TAG, "No recognition candidates")
                        mainHandler.post {
                            recognitionCallback?.invoke(null)
                            recognitionCallback = null
                        }
                    }
                }?.addOnFailureListener { e ->
                    Log.e(TAG, "Recognition failed", e)
                    mainHandler.post {
                        recognitionCallback?.invoke("识别失败: ${e.message}")
                        recognitionCallback = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                mainHandler.post {
                    recognitionCallback?.invoke("识别错误: ${e.message}")
                    recognitionCallback = null
                }
            }
        }
    }
    
    /**
     * 清除当前手写内容
     */
    fun clear() {
        currentInk = null
        currentStroke = null
        strokeHistory.clear()
        
        Log.d(TAG, "Handwriting cleared")
    }
    
    /**
     * 设置识别语言
     */
    fun setLanguage(language: String) {
        if (currentLanguage != language) {
            currentLanguage = language
            initializeRecognitionModel()
        }
    }
    
    /**
     * 获取当前语言
     */
    fun getCurrentLanguage(): String {
        return currentLanguage
    }
    
    /**
     * 获取支持的语言列表
     */
    fun getSupportedLanguages(): List<String> {
        return listOf(
            "zh-Hans", // 简体中文
            "zh-Hant", // 繁体中文
            "en-US",   // 英文（美国）
            "ja-JP",   // 日文
            "ko-KR",   // 韩文
            "fr-FR",   // 法文
            "de-DE",   // 德文
            "es-ES",   // 西班牙文
            "ar-SA",   // 阿拉伯文
            "ru-RU"    // 俄文
        )
    }
    
    /**
     * 获取笔画历史
     */
    fun getStrokeHistory(): List<Pair<Long, List<PointF>>> {
        return strokeHistory.toList()
    }
    
    /**
     * 获取笔画数量
     */
    fun getStrokeCount(): Int {
        return strokeHistory.size
    }
    
    /**
     * 获取当前墨水
     */
    fun getCurrentInk(): Ink? {
        return currentInk
    }
    
    /**
     * 获取识别统计
     */
    fun getRecognitionStatistics(): Map<String, Any> {
        val avgTime = if (recognitionCount > 0) totalRecognitionTime / recognitionCount else 0
        val successRate = if (recognitionCount > 0) successCount.toFloat() / recognitionCount * 100 else 0f
        
        return mapOf(
            "total_recognitions" to recognitionCount,
            "successful_recognitions" to successCount,
            "success_rate" to successRate,
            "average_time_ms" to avgTime,
            "stroke_count" to strokeHistory.size,
            "current_language" to currentLanguage,
            "enabled" to enabled,
            "initialized" to initialized
        )
    }
    
    /**
     * 导出手写数据为图片
     */
    fun exportToBitmap(width: Int = CANVAS_WIDTH, height: Int = CANVAS_HEIGHT): Bitmap? {
        if (strokeHistory.isEmpty()) {
            return null
        }
        
        // 创建位图
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 绘制背景
        canvas.drawColor(BACKGROUND_COLOR)
        
        // 创建画笔
        val paint = Paint().apply {
            color = INK_COLOR
            strokeWidth = STROKE_WIDTH
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        
        // 计算缩放和平移
        val bounds = calculateStrokeBounds()
        val scale = calculateScale(bounds, width, height)
        val offset = calculateOffset(bounds, width, height, scale)
        
        // 绘制所有笔画
        for ((_, points) in strokeHistory) {
            if (points.size < 2) continue
            
            val path = Path()
            val firstPoint = points[0]
            val scaledX = (firstPoint.x - bounds.left) * scale + offset.x
            val scaledY = (firstPoint.y - bounds.top) * scale + offset.y
            path.moveTo(scaledX, scaledY)
            
            for (i in 1 until points.size) {
                val point = points[i]
                val x = (point.x - bounds.left) * scale + offset.x
                val y = (point.y - bounds.top) * scale + offset.y
                path.lineTo(x, y)
            }
            
            canvas.drawPath(path, paint)
        }
        
        return bitmap
    }
    
    /**
     * 笔迹回放
     */
    fun replayStrokes(
        intervalMs: Long = 100,
        callback: (List<PointF>?, Int) -> Unit
    ) {
        if (strokeHistory.isEmpty()) {
            callback(null, 0)
            return
        }
        
        Thread {
            var strokeIndex = 0
            for ((timestamp, points) in strokeHistory) {
                mainHandler.post {
                    callback(points, strokeIndex)
                }
                strokeIndex++
                
                if (strokeIndex < strokeHistory.size) {
                    val nextTimestamp = strokeHistory[strokeIndex].first
                    val delay = (nextTimestamp - timestamp).coerceAtLeast(intervalMs)
                    Thread.sleep(delay)
                }
            }
            
            mainHandler.post {
                callback(null, strokeHistory.size)
            }
        }.start()
    }
    
    /**
     * 撤销最后一笔
     */
    fun undoLastStroke(): Boolean {
        if (strokeHistory.isEmpty()) {
            return false
        }
        
        // 移除最后一笔
        strokeHistory.removeAt(strokeHistory.size - 1)
        
        // 重建墨水
        rebuildInkFromHistory()
        
        Log.d(TAG, "Undo last stroke, remaining strokes: ${strokeHistory.size}")
        return true
    }
    
    /**
     * 重做最后一笔
     */
    fun redoLastStroke(): Boolean {
        // 这里需要实现重做逻辑
        // 简化实现：暂时不支持重做
        return false
    }
    
    /**
     * 获取笔画边界
     */
    private fun calculateStrokeBounds(): android.graphics.RectF {
        if (strokeHistory.isEmpty()) {
            return android.graphics.RectF(0f, 0f, 1f, 1f)
        }
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        for ((_, points) in strokeHistory) {
            for (point in points) {
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }
        }
        
        // 添加一些边距
        val margin = 10f
        return android.graphics.RectF(
            minX - margin,
            minY - margin,
            maxX + margin,
            maxY + margin
        )
    }
    
    /**
     * 计算缩放比例
     */
    private fun calculateScale(
        bounds: android.graphics.RectF,
        width: Int,
        height: Int
    ): Float {
        val scaleX = width / bounds.width()
        val scaleY = height / bounds.height()
        return minOf(scaleX, scaleY) * 0.9f // 留10%边距
    }
    
    /**
     * 计算偏移量
     */
    private fun calculateOffset(
        bounds: android.graphics.RectF,
        width: Int,
        height: Int,
        scale: Float
    ): PointF {
        val scaledWidth = bounds.width() * scale
        val scaledHeight = bounds.height() * scale
        val offsetX = (width - scaledWidth) / 2
        val offsetY = (height - scaledHeight) / 2
        return PointF(offsetX, offsetY)
    }
    
    /**
     * 从历史重建墨水
     */
    private fun rebuildInkFromHistory() {
        currentInk = null
        
        val inkBuilder = Ink.builder()
        var timestamp = System.currentTimeMillis()
        
        for ((_, points) in strokeHistory) {
            if (points.isEmpty()) continue
            
            val strokeBuilder = Ink.Stroke.builder()
            points.forEach { point ->
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, timestamp))
                timestamp += 10 // 每点增加10ms
            }
            
            inkBuilder.addStroke(strokeBuilder.build())
        }
        
        currentInk = inkBuilder.build()
    }
    
    /**
     * 初始化识别模型
     */
    private fun initializeRecognitionModel() {
        try {
            // 释放旧模型
            recognizer?.close()
            
            // 创建模型标识符
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(currentLanguage)
            if (modelIdentifier == null) {
                Log.e(TAG, "Unsupported language: $currentLanguage")
                return
            }
            
            // 创建模型
            model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            
            // 创建识别器
            val options = DigitalInkRecognizerOptions.builder(model!!).build()
            recognizer = DigitalInkRecognition.getClient(options)
            
            Log.d(TAG, "Recognition model initialized for language: $currentLanguage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recognition model", e)
        }
    }
    
    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(): Boolean {
        return model?.isDownloaded ?: false
    }
    
    /**
     * 下载模型
     */
    fun downloadModel(callback: (Boolean) -> Unit) {
        model?.let {
            if (it.isDownloaded) {
                callback(true)
                return
            }
            
            it.download().addOnSuccessListener {
                Log.d(TAG, "Model downloaded successfully")
                callback(true)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Model download failed", e)
                callback(false)
            }
        } ?: run {
            callback(false)
        }
    }
    
    /**
     * 获取模型下载状态
     */
    fun getModelDownloadStatus(): String {
        return when {
            model == null -> "NOT_INITIALIZED"
            model?.isDownloaded == true -> "DOWNLOADED"
            else -> "NOT_DOWNLOADED"
        }
    }
}