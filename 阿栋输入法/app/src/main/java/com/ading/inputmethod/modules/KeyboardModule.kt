package com.ading.inputmethod.modules

import android.content.Context

/**
 * 键盘模块接口
 * 所有功能模块必须实现此接口
 */
interface KeyboardModule {
    
    /**
     * 获取模块名称
     */
    fun getName(): String
    
    /**
     * 获取模块描述
     */
    fun getDescription(): String
    
    /**
     * 初始化模块
     */
    fun initialize()
    
    /**
     * 释放模块资源
     */
    fun release()
    
    /**
     * 模块是否启用
     */
    fun isEnabled(): Boolean
    
    /**
     * 设置模块启用状态
     */
    fun setEnabled(enabled: Boolean)
    
    /**
     * 模块是否已初始化
     */
    fun isInitialized(): Boolean
    
    /**
     * 是否可以处理按键事件
     */
    fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean = false): Boolean
    
    /**
     * 处理按键事件
     * @return true表示已处理，false表示未处理
     */
    fun handleKeyEvent(keyCode: Int, isLongPress: Boolean = false): Boolean
    
    /**
     * 获取最后使用时间
     */
    fun getLastUsedTime(): Long
    
    /**
     * 保存模块状态
     */
    fun saveState()
    
    /**
     * 恢复模块状态
     */
    fun restoreState()
    
    /**
     * 处理配置变更
     */
    fun onConfigurationChanged()
    
    /**
     * 处理低内存警告
     */
    fun onLowMemory()
}

/**
 * 文本处理模块接口
 */
interface TextProcessingModule {
    
    /**
     * 处理文本输入
     */
    fun processTextInput(text: String)
    
    /**
     * 获取预测结果
     */
    fun getPredictions(input: String): List<String>
    
    /**
     * 学习用户输入
     */
    fun learnFromInput(input: String)
}

/**
 * 基础键盘模块抽象类
 */
abstract class BaseKeyboardModule(
    protected val context: Context
) : KeyboardModule {
    
    protected var enabled = true
    protected var initialized = false
    protected var lastUsedTime = 0L
    
    override fun getName(): String {
        return this::class.simpleName ?: "UnknownModule"
    }
    
    override fun getDescription(): String {
        return "Base keyboard module"
    }
    
    override fun initialize() {
        if (!initialized) {
            onInitialize()
            initialized = true
        }
    }
    
    override fun release() {
        if (initialized) {
            onRelease()
            initialized = false
        }
    }
    
    override fun isEnabled(): Boolean {
        return enabled
    }
    
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled && !initialized) {
            initialize()
        }
    }
    
    override fun isInitialized(): Boolean {
        return initialized
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    override fun getLastUsedTime(): Long {
        return lastUsedTime
    }
    
    override fun saveState() {
        // 子类可以重写此方法保存状态
    }
    
    override fun restoreState() {
        // 子类可以重写此方法恢复状态
    }
    
    override fun onConfigurationChanged() {
        // 子类可以重写此方法处理配置变更
    }
    
    override fun onLowMemory() {
        // 子类可以重写此方法处理低内存
    }
    
    /**
     * 初始化具体实现
     */
    protected abstract fun onInitialize()
    
    /**
     * 释放具体实现
     */
    protected abstract fun onRelease()
}

/**
 * 拼音输入模块
 */
class PinyinModule(context: Context) : BaseKeyboardModule(context), TextProcessingModule {
    
    private val dictionary = mutableMapOf<String, List<String>>()
    private val userLearnedWords = mutableMapOf<String, Int>()
    
    override fun getDescription(): String {
        return "拼音输入模块，支持智能联想和词频学习"
    }
    
    override fun onInitialize() {
        // 加载拼音词库
        loadDictionary()
        
        // 加载用户学习数据
        loadUserLearnedWords()
    }
    
    override fun onRelease() {
        // 保存用户学习数据
        saveUserLearnedWords()
        
        // 清理字典
        dictionary.clear()
        userLearnedWords.clear()
    }
    
    override fun processTextInput(text: String) {
        // 学习用户输入
        learnFromInput(text)
    }
    
    override fun getPredictions(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val predictions = mutableListOf<String>()
        
        // 1. 首先检查用户学习的高频词
        val userWords = userLearnedWords.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .filter { it.contains(input, ignoreCase = true) }
        
        predictions.addAll(userWords)
        
        // 2. 然后检查字典匹配
        val dictWords = dictionary[input.lowercase()] ?: emptyList()
        predictions.addAll(dictWords.take(10 - predictions.size))
        
        // 3. 如果还不够，使用模糊匹配
        if (predictions.size < 10) {
            val fuzzyMatches = dictionary.entries
                .filter { it.key.startsWith(input.lowercase()) }
                .flatMap { it.value }
                .take(10 - predictions.size)
            
            predictions.addAll(fuzzyMatches)
        }
        
        return predictions.distinct().take(10)
    }
    
    override fun learnFromInput(input: String) {
        if (input.isNotEmpty()) {
            userLearnedWords[input] = userLearnedWords.getOrDefault(input, 0) + 1
            
            // 限制学习词条数量
            if (userLearnedWords.size > 1000) {
                val sorted = userLearnedWords.entries.sortedByDescending { it.value }
                val toRemove = sorted.drop(1000).map { it.key }
                toRemove.forEach { userLearnedWords.remove(it) }
            }
        }
    }
    
    private fun loadDictionary() {
        // 这里应该从assets或数据库加载拼音词库
        // 简化实现：添加一些示例词
        dictionary["wo"] = listOf("我", "握", "窝", "卧", "沃")
        dictionary["ni"] = listOf("你", "泥", "逆", "拟", "妮")
        dictionary["ta"] = listOf("他", "她", "它", "塌", "踏")
        dictionary["hao"] = listOf("好", "号", "豪", "耗", "浩")
        dictionary["shi"] = listOf("是", "时", "事", "十", "石")
        dictionary["de"] = listOf("的", "得", "地", "德", "嘚")
        dictionary["le"] = listOf("了", "乐", "勒", "叻", "鳓")
        dictionary["zai"] = listOf("在", "再", "灾", "载", "宰")
        dictionary["you"] = listOf("有", "又", "由", "油", "游")
        dictionary["mei"] = listOf("没", "每", "美", "梅", "煤")
    }
    
    private fun loadUserLearnedWords() {
        // 从SharedPreferences加载用户学习数据
        val prefs = context.getSharedPreferences("pinyin_learning", Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            if (value is Int) {
                userLearnedWords[key] = value
            }
        }
    }
    
    private fun saveUserLearnedWords() {
        // 保存到SharedPreferences
        val prefs = context.getSharedPreferences("pinyin_learning", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        userLearnedWords.forEach { (key, value) ->
            editor.putInt(key, value)
        }
        editor.apply()
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 拼音模块主要处理文本输入，不直接处理按键
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    override fun onConfigurationChanged() {
        // 重新加载词库以适应配置变更
        loadDictionary()
    }
    
    override fun onLowMemory() {
        // 清理缓存
        dictionary.clear()
    }
}

/**
 * 五笔输入模块
 */
class WubiModule(context: Context) : BaseKeyboardModule(context), TextProcessingModule {
    
    private val wubiDictionary = mutableMapOf<String, String>()
    
    override fun getDescription(): String {
        return "五笔输入模块，支持86版和98版五笔"
    }
    
    override fun onInitialize() {
        loadWubiDictionary()
    }
    
    override fun onRelease() {
        wubiDictionary.clear()
    }
    
    override fun processTextInput(text: String) {
        // 五笔模块不学习用户输入
    }
    
    override fun getPredictions(input: String): List<String> {
        if (input.length != 4) return emptyList()
        
        return wubiDictionary.entries
            .filter { it.key.startsWith(input) }
            .map { it.value }
            .take(10)
    }
    
    override fun learnFromInput(input: String) {
        // 五笔模块不学习用户输入
    }
    
    private fun loadWubiDictionary() {
        // 这里应该从assets加载五笔词库
        // 简化实现：添加一些示例
        wubiDictionary["ggll"] = "一"
        wubiDictionary["hhll"] = "丨"
        wubiDictionary["ttll"] = "丿"
        wubiDictionary["yyll"] = "丶"
        wubiDictionary["nnll"] = "乙"
        wubiDictionary["trnt"] = "我"
        wubiDictionary["wqiy"] = "你"
        wubiDictionary["wbn"] = "他"
        wubiDictionary["vbg"] = "好"
        wubiDictionary["jghu"] = "是"
        wubiDictionary["rqyy"] = "的"
        wubiDictionary["bn"] = "了"
        wubiDictionary["dhfd"] = "在"
        wubiDictionary["def"] = "有"
        wubiDictionary["imcy"] = "没"
    }
}

/**
 * 英文输入模块
 */
class EnglishModule(context: Context) : BaseKeyboardModule(context), TextProcessingModule {
    
    private val englishDictionary = mutableSetOf<String>()
    private val userLearnedWords = mutableMapOf<String, Int>()
    
    override fun getDescription(): String {
        return "英文输入模块，支持自动纠正和单词预测"
    }
    
    override fun onInitialize() {
        loadEnglishDictionary()
        loadUserLearnedWords()
    }
    
    override fun onRelease() {
        saveUserLearnedWords()
        englishDictionary.clear()
        userLearnedWords.clear()
    }
    
    override fun processTextInput(text: String) {
        learnFromInput(text)
    }
    
    override fun getPredictions(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val predictions = mutableListOf<String>()
        
        // 1. 用户学习的高频词
        val userWords = userLearnedWords.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it.startsWith(input, ignoreCase = true) }
            .take(5)
        
        predictions.addAll(userWords)
        
        // 2. 字典匹配
        val dictWords = englishDictionary
            .filter { it.startsWith(input, ignoreCase = true) }
            .take(10 - predictions.size)
        
        predictions.addAll(dictWords)
        
        // 3. 自动纠正建议
        if (predictions.size < 3 && input.length > 2) {
            val corrections = getAutoCorrections(input)
            predictions.addAll(corrections.take(3 - predictions.size))
        }
        
        return predictions.distinct().take(10)
    }
    
    override fun learnFromInput(input: String) {
        val word = input.trim()
        if (word.isNotEmpty() && word.matches(Regex("[a-zA-Z]+"))) {
            userLearnedWords[word.lowercase()] = userLearnedWords.getOrDefault(word.lowercase(), 0) + 1
            
            // 限制学习词条数量
            if (userLearnedWords.size > 1000) {
                val sorted = userLearnedWords.entries.sortedByDescending { it.value }
                val toRemove = sorted.drop(1000).map { it.key }
                toRemove.forEach { userLearnedWords.remove(it) }
            }
        }
    }
    
    private fun loadEnglishDictionary() {
        // 常见英文单词
        val commonWords = listOf(
            "hello", "world", "android", "keyboard", "input", "method",
            "english", "dictionary", "prediction", "correction", "auto",
            "text", "word", "sentence", "paragraph", "document",
            "the", "and", "you", "that", "have", "this", "with",
            "from", "they", "what", "when", "where", "why", "how"
        )
        
        englishDictionary.addAll(commonWords)
    }
    
    private fun loadUserLearnedWords() {
        val prefs = context.getSharedPreferences("english_learning", Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            if (value is Int) {
                userLearnedWords[key] = value
            }
        }
    }
    
    private fun saveUserLearnedWords() {
        val prefs = context.getSharedPreferences("english_learning", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        userLearnedWords.forEach { (key, value) ->
            editor.putInt(key, value)
        }
        editor.apply()
    }
    
    private fun getAutoCorrections(word: String): List<String> {
        val corrections = mutableListOf<String>()
        val lowerWord = word.lowercase()
        
        // 简单的编辑距离算法（简化版）
        englishDictionary.forEach { dictWord ->
            if (isSimilar(lowerWord, dictWord)) {
                corrections.add(dictWord)
            }
        }
        
        return corrections.sorted().take(5)
    }
    
    private fun isSimilar(word1: String, word2: String): Boolean {
        if (word1 == word2) return true
        
        // 简单的相似度检查
        val len1 = word1.length
        val len2 = word2.length
        
        if (abs(len1 - len2) > 2) return false
        
        // 检查共同前缀
        val minLen = minOf(len1, len2)
        var commonPrefix = 0
        for (i in 0 until minLen) {
            if (word1[i] == word2[i]) {
                commonPrefix++
            } else {
                break
            }
        }
        
        return commonPrefix >= minLen - 2
    }
    
    private fun abs(x: Int) = if (x < 0) -x else x
}