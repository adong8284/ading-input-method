package com.ading.inputmethod.modules

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 滑行输入模块
 * 自实现滑动手势识别算法，支持Trie树词库匹配和滑动路径预测
 */
class SwipeTypingModule(context: Context) : BaseKeyboardModule(context), TextProcessingModule {
    
    companion object {
        private const val TAG = "SwipeTypingModule"
        private const val PREFS_NAME = "swipe_typing_prefs"
        private const val KEY_ENABLED = "swipe_enabled"
        private const val KEY_VIBRATION = "swipe_vibration"
        private const val KEY_TRAIL = "swipe_trail"
        private const val KEY_SENSITIVITY = "swipe_sensitivity"
        private const val KEY_LEARNING = "swipe_learning"
        
        // 键盘布局定义（QWERTY）
        private val KEYBOARD_LAYOUT = mapOf(
            'q' to PointF(0f, 0f), 'w' to PointF(1f, 0f), 'e' to PointF(2f, 0f), 'r' to PointF(3f, 0f), 't' to PointF(4f, 0f), 'y' to PointF(5f, 0f), 'u' to PointF(6f, 0f), 'i' to PointF(7f, 0f), 'o' to PointF(8f, 0f), 'p' to PointF(9f, 0f),
            'a' to PointF(0.5f, 1f), 's' to PointF(1.5f, 1f), 'd' to PointF(2.5f, 1f), 'f' to PointF(3.5f, 1f), 'g' to PointF(4.5f, 1f), 'h' to PointF(5.5f, 1f), 'j' to PointF(6.5f, 1f), 'k' to PointF(7.5f, 1f), 'l' to PointF(8.5f, 1f),
            'z' to PointF(1f, 2f), 'x' to PointF(2f, 2f), 'c' to PointF(3f, 2f), 'v' to PointF(4f, 2f), 'b' to PointF(5f, 2f), 'n' to PointF(6f, 2f), 'm' to PointF(7f, 2f)
        )
        
        // 敏感度级别
        private const val SENSITIVITY_LOW = 0.7f
        private const val SENSITIVITY_MEDIUM = 0.5f
        private const val SENSITIVITY_HIGH = 0.3f
    }
    
    // Trie树节点
    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord = false
        var frequency = 0
    }
    
    // 根节点
    private val root = TrieNode()
    
    // 滑动轨迹
    private val swipePath = mutableListOf<PointF>()
    private var isSwiping = false
    private var swipeStartTime = 0L
    
    // 用户学习数据
    private val userLearnedWords = mutableMapOf<String, Int>()
    
    // 设置
    private var swipeEnabled = true
    private var vibrationEnabled = true
    private var trailEnabled = true
    private var sensitivity = SENSITIVITY_MEDIUM
    private var learningEnabled = true
    
    // 共享偏好
    private lateinit var prefs: SharedPreferences
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 回调
    private var swipeCallback: ((String) -> Unit)? = null
    private var trailCallback: ((List<PointF>) -> Unit)? = null
    
    override fun getDescription(): String {
        return "滑行输入模块，自实现滑动手势识别算法，支持Trie树词库匹配和滑动路径预测"
    }
    
    override fun onInitialize() {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 加载设置
        loadSettings()
        
        // 加载词库
        loadDictionary()
        
        // 加载用户学习数据
        loadUserLearnedWords()
        
        Log.d(TAG, "Swipe typing module initialized")
    }
    
    override fun onRelease() {
        // 保存用户学习数据
        saveUserLearnedWords()
        
        // 清理资源
        swipePath.clear()
        
        Log.d(TAG, "Swipe typing module released")
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 滑行输入模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    override fun processTextInput(text: String) {
        if (learningEnabled) {
            learnFromInput(text)
        }
    }
    
    override fun getPredictions(input: String): List<String> {
        // 滑行输入不使用传统预测
        return emptyList()
    }
    
    override fun learnFromInput(text: String) {
        val word = text.trim().lowercase()
        if (word.isNotEmpty() && word.matches(Regex("[a-z]+"))) {
            userLearnedWords[word] = userLearnedWords.getOrDefault(word, 0) + 1
            
            // 限制学习词条数量
            if (userLearnedWords.size > 1000) {
                val sorted = userLearnedWords.entries.sortedByDescending { it.value }
                val toRemove = sorted.drop(1000).map { it.key }
                toRemove.forEach { userLearnedWords.remove(it) }
            }
            
            // 添加到Trie树
            addWordToTrie(word, isUserLearned = true)
            
            Log.d(TAG, "Learned word: $word (frequency: ${userLearnedWords[word]})")
        }
    }
    
    /**
     * 开始滑动手势
     */
    fun startSwipe(x: Float, y: Float) {
        if (!swipeEnabled) return
        
        swipePath.clear()
        swipePath.add(PointF(x, y))
        isSwiping = true
        swipeStartTime = System.currentTimeMillis()
        
        Log.d(TAG, "Swipe started at ($x, $y)")
    }
    
    /**
     * 更新滑动手势
     */
    fun updateSwipe(x: Float, y: Float) {
        if (!isSwiping) return
        
        val lastPoint = swipePath.last()
        val distance = calculateDistance(lastPoint.x, lastPoint.y, x, y)
        
        // 只有移动足够距离才添加新点（减少噪声）
        if (distance > sensitivity) {
            swipePath.add(PointF(x, y))
            
            // 回调轨迹更新
            if (trailEnabled) {
                trailCallback?.invoke(swipePath.toList())
            }
            
            Log.d(TAG, "Swipe updated to ($x, $y), distance: $distance")
        }
    }
    
    /**
     * 结束滑动手势并识别
     */
    fun endSwipe(x: Float, y: Float): String? {
        if (!isSwiping) return null
        
        updateSwipe(x, y)
        isSwiping = false
        
        val swipeDuration = System.currentTimeMillis() - swipeStartTime
        val swipeLength = calculateSwipeLength()
        
        Log.d(TAG, "Swipe ended. Points: ${swipePath.size}, Duration: ${swipeDuration}ms, Length: $swipeLength")
        
        // 识别单词
        val recognizedWord = recognizeSwipe()
        
        // 清除轨迹
        swipePath.clear()
        
        // 提供振动反馈
        if (vibrationEnabled && recognizedWord != null) {
            provideHapticFeedback()
        }
        
        return recognizedWord
    }
    
    /**
     * 取消滑动手势
     */
    fun cancelSwipe() {
        isSwiping = false
        swipePath.clear()
        
        Log.d(TAG, "Swipe cancelled")
    }
    
    /**
     * 识别滑动轨迹
     */
    fun recognizeSwipe(startX: Float, startY: Float, endX: Float, endY: Float): String? {
        // 简化接口，创建虚拟轨迹
        swipePath.clear()
        swipePath.add(PointF(startX, startY))
        swipePath.add(PointF(endX, endY))
        
        return recognizeSwipe()
    }
    
    /**
     * 识别滑动轨迹（内部实现）
     */
    private fun recognizeSwipe(): String? {
        if (swipePath.size < 2) {
            Log.w(TAG, "Swipe path too short")
            return null
        }
        
        // 将轨迹点映射到键盘按键
        val keySequence = mapPathToKeys()
        if (keySequence.isEmpty()) {
            Log.w(TAG, "No keys mapped from swipe path")
            return null
        }
        
        Log.d(TAG, "Mapped key sequence: $keySequence")
        
        // 在Trie树中搜索匹配的单词
        val candidates = findCandidates(keySequence)
        
        // 排序候选词（频率 + 编辑距离）
        val sortedCandidates = candidates.sortedWith(compareByDescending<Pair<String, Int>> { it.second }
            .thenBy { calculateEditDistance(keySequence, it.first) })
        
        // 返回最佳匹配
        return sortedCandidates.firstOrNull()?.first
    }
    
    /**
     * 将轨迹点映射到键盘按键
     */
    private fun mapPathToKeys(): String {
        val keySequence = StringBuilder()
        
        // 对轨迹点进行采样，减少计算量
        val sampledPath = samplePath(swipePath, 20)
        
        sampledPath.forEach { point ->
            val nearestKey = findNearestKey(point.x, point.y)
            nearestKey?.let { key ->
                // 避免重复按键
                if (keySequence.isEmpty() || keySequence.last() != key) {
                    keySequence.append(key)
                }
            }
        }
        
        return keySequence.toString()
    }
    
    /**
     * 采样轨迹点
     */
    private fun samplePath(path: List<PointF>, maxPoints: Int): List<PointF> {
        if (path.size <= maxPoints) return path
        
        val sampled = mutableListOf<PointF>()
        val step = path.size.toFloat() / maxPoints
        
        for (i in 0 until maxPoints) {
            val index = (i * step).toInt()
            sampled.add(path[index])
        }
        
        return sampled
    }
    
    /**
     * 查找最近的按键
     */
    private fun findNearestKey(x: Float, y: Float): Char? {
        var minDistance = Float.MAX_VALUE
        var nearestKey: Char? = null
        
        KEYBOARD_LAYOUT.forEach { (key, keyPoint) ->
            // 将键盘坐标转换为实际坐标（这里需要根据实际键盘布局调整）
            val actualX = keyPoint.x * 100 // 假设每个键宽100单位
            val actualY = keyPoint.y * 100 // 假设每个键高100单位
            
            val distance = calculateDistance(x, y, actualX, actualY)
            if (distance < minDistance && distance < 50) { // 50单位阈值
                minDistance = distance
                nearestKey = key
            }
        }
        
        return nearestKey
    }
    
    /**
     * 在Trie树中查找候选词
     */
    private fun findCandidates(keySequence: String): List<Pair<String, Int>> {
        val candidates = mutableListOf<Pair<String, Int>>()
        
        // 搜索Trie树
        searchTrie(root, keySequence, 0, StringBuilder(), candidates)
        
        // 添加用户学习的高频词
        userLearnedWords.forEach { (word, frequency) ->
            if (isSimilarSequence(keySequence, word)) {
                candidates.add(Pair(word, frequency * 2)) // 用户学习词权重加倍
            }
        }
        
        return candidates
    }
    
    /**
     * 搜索Trie树
     */
    private fun searchTrie(
        node: TrieNode,
        sequence: String,
        index: Int,
        currentWord: StringBuilder,
        candidates: MutableList<Pair<String, Int>>
    ) {
        if (index >= sequence.length) {
            if (node.isWord) {
                candidates.add(Pair(currentWord.toString(), node.frequency))
            }
            return
        }
        
        val currentChar = sequence[index]
        
        // 检查当前字符
        node.children[currentChar]?.let { child ->
            currentWord.append(currentChar)
            searchTrie(child, sequence, index + 1, currentWord, candidates)
            currentWord.deleteCharAt(currentWord.length - 1)
        }
        
        // 允许跳过一个字符（容错）
        if (index < sequence.length - 1) {
            searchTrie(node, sequence, index + 1, currentWord, candidates)
        }
        
        // 允许替换一个字符（容错）
        node.children.forEach { (char, child) ->
            if (char != currentChar) {
                currentWord.append(char)
                searchTrie(child, sequence, index + 1, currentWord, candidates)
                currentWord.deleteCharAt(currentWord.length - 1)
            }
        }
    }
    
    /**
     * 检查序列相似度
     */
    private fun isSimilarSequence(seq1: String, seq2: String): Boolean {
        if (seq1.isEmpty() || seq2.isEmpty()) return false
        
        // 简单的相似度检查
        val len1 = seq1.length
        val len2 = seq2.length
        
        if (abs(len1 - len2) > 2) return false
        
        // 计算编辑距离
        val distance = calculateEditDistance(seq1, seq2)
        val maxLen = maxOf(len1, len2)
        
        return distance.toFloat() / maxLen < 0.3f // 30%容错率
    }
    
    /**
     * 计算编辑距离
     */
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) {
            for (j in 0..s2.length) {
                when {
                    i == 0 -> dp[i][j] = j
                    j == 0 -> dp[i][j] = i
                    s1[i - 1] == s2[j - 1] -> dp[i][j] = dp[i - 1][j - 1]
                    else -> dp[i][j] = 1 + minOf(
                        dp[i - 1][j],    // 删除
                        dp[i][j - 1],    // 插入
                        dp[i - 1][j - 1] // 替换
                    )
                }
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    /**
     * 计算两点距离
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * 计算滑动轨迹总长度
     */
    private fun calculateSwipeLength(): Float {
        var length = 0f
        
        for (i in 1 until swipePath.size) {
            val p1 = swipePath[i - 1]
            val p2 = swipePath[i]
            length += calculateDistance(p1.x, p1.y, p2.x, p2.y)
        }
        
        return length
    }
    
    /**
     * 添加单词到Trie树
     */
    private fun addWordToTrie(word: String, frequency: Int = 1, isUserLearned: Boolean = false) {
        var node = root
        
        for (char in word) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        
        node.isWord = true
        if (isUserLearned) {
            node.frequency += frequency * 10 // 用户学习词权重更高
        } else {
            node.frequency += frequency
        }
    }
    
    /**
     * 加载词库
     */
    private fun loadDictionary() {
        // 这里应该从assets或数据库加载词库
        // 简化实现：添加一些常用英文单词
        
        val commonWords = listOf(
            "the" to 1000,
            "and" to 900,
            "you" to 800,
            "that" to 700,
            "have" to 600,
            "this" to 500,
            "with" to 400,
            "from" to 300,
            "they" to 200,
            "what" to 100,
            "when" to 90,
            "where" to 80,
            "why" to 70,
            "how" to 60,
            "hello" to 50,
            "world" to 40,
            "android" to 30,
            "keyboard" to 20,
            "input" to 10,
            "method" to 5
        )
        
        commonWords.forEach { (word, freq) ->
            addWordToTrie(word, freq)
        }
        
        Log.d(TAG, "Dictionary loaded with ${commonWords.size} words")
    }
    
    /**
     * 加载用户学习数据
     */
    private fun loadUserLearnedWords() {
        val jsonString = prefs.getString("user_learned_words", "{}")
        
        try {
            val json = org.json.JSONObject(jsonString)
            val keys = json.keys()
            
            while (keys.hasNext()) {
                val word = keys.next()
                val frequency = json.getInt(word)
                userLearnedWords[word] = frequency
            }
            
            Log.d(TAG, "Loaded ${userLearnedWords.size} user learned words")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user learned words", e)
            userLearnedWords.clear()
        }
    }
    
    /**
     * 保存用户学习数据
     */
    private fun saveUserLearnedWords() {
        try {
            val json = org.json.JSONObject()
            userLearnedWords.forEach { (word, frequency) ->
                json.put(word, frequency)
            }
            
            prefs.edit {
                putString("user_learned_words", json.toString())
            }
            
            Log.d(TAG, "Saved ${userLearnedWords.size} user learned words")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user learned words", e)
        }
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        swipeEnabled = prefs.getBoolean(KEY_ENABLED, true)
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true)
        trailEnabled = prefs.getBoolean(KEY_TRAIL, true)
        learningEnabled = prefs.getBoolean(KEY_LEARNING, true)
        
        val sensitivityValue = prefs.getFloat(KEY_SENSITIVITY, SENSITIVITY_MEDIUM)
        sensitivity = when {
            sensitivityValue <= SENSITIVITY_HIGH -> SENSITIVITY_HIGH
            sensitivityValue <= SENSITIVITY_MEDIUM -> SENSITIVITY_MEDIUM
            else -> SENSITIVITY_LOW
        }
        
        Log.d(TAG, "Settings loaded: enabled=$swipeEnabled, vibration=$vibrationEnabled, trail=$trailEnabled, sensitivity=$sensitivity")
    }
    
    /**
     * 保存设置
     */
    fun saveSettings() {
        prefs.edit {
            putBoolean(KEY_ENABLED, swipeEnabled)
            putBoolean(KEY_VIBRATION, vibrationEnabled)
            putBoolean(KEY_TRAIL, trailEnabled)
            putBoolean(KEY_LEARNING, learningEnabled)
            putFloat(KEY_SENSITIVITY, sensitivity)
        }
        
        Log.d(TAG, "Settings saved")
    }
    
    /**
     * 设置滑行输入是否启用
     */
    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
    }
    
    /**
     * 设置振动反馈是否启用
     */
    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
    }
    
    /**
     * 设置轨迹显示是否启用
     */
    fun setTrailEnabled(enabled: Boolean) {
        trailEnabled = enabled
    }
    
    /**
     * 设置敏感度
     */
    fun setSensitivity(level: Float) {
        sensitivity = level.coerceIn(SENSITIVITY_HIGH, SENSITIVITY_LOW)
    }
    
    /**
     * 设置学习功能是否启用
     */
    fun setLearningEnabled(enabled: Boolean) {
        learningEnabled = enabled
    }
    
    /**
     * 设置滑动回调
     */
    fun setSwipeCallback(callback: (String) -> Unit) {
        swipeCallback = callback
    }
    
    /**
     * 设置轨迹回调
     */
    fun setTrailCallback(callback: (List<PointF>) -> Unit) {
        trailCallback = callback
    }
    
    /**
     * 提供触觉反馈
     */
    private fun provideHapticFeedback() {
        // 这里应该调用系统的振动反馈
        // 简化实现：记录日志
        Log.d(TAG, "Haptic feedback triggered")
    }
    
    /**
     * 获取模块统计
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "enabled" to swipeEnabled,
            "vibration_enabled" to vibrationEnabled,
            "trail_enabled" to trailEnabled,
            "learning_enabled" to learningEnabled,
            "sensitivity" to sensitivity,
            "dictionary_size" to countTrieWords(root),
            "user_learned_words" to userLearnedWords.size,
            "last_used" to lastUsedTime
        )
    }
    
    /**
     * 统计Trie树中的单词数量
     */
    private fun countTrieWords(node: TrieNode): Int {
        var count = if (node.isWord) 1 else 0
        
        node.children.values.forEach { child ->
            count += countTrieWords(child)
        }
        
        return count
    }
    
    /**
     * 导出用户学习数据
     */
    fun exportLearningData(): String {
        return org.json.JSONObject(userLearnedWords).toString(2)
    }
    
    /**
     * 导入用户学习数据
     */
    fun importLearningData(jsonString: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonString)
            val keys = json.keys()
            
            userLearnedWords.clear()
            
            while (keys.hasNext()) {
                val word = keys.next()
                val frequency = json.getInt(word)
                userLearnedWords[word] = frequency
                addWordToTrie(word, frequency, true)
            }
            
            saveUserLearnedWords()
            Log.d(TAG, "Imported ${userLearnedWords.size} user learned words")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing learning data", e)
            false
        }
    }
    
    /**
     * 清除用户学习数据
     */
    fun clearLearningData() {
        userLearnedWords.clear()
        saveUserLearnedWords()
        Log.d(TAG, "Cleared user learning data")
    }
    
    /**
     * 获取当前滑动状态
     */
    fun getSwipeState(): Map<String, Any> {
        return mapOf(
            "is_swiping" to isSwiping,
            "path_points" to swipePath.size,
            "start_time" to swipeStartTime,
            "duration_ms" to if (isSwiping) System.currentTimeMillis() - swipeStartTime else 0
        )
    }
}