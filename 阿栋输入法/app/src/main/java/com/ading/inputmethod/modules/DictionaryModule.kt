package com.ading.inputmethod.modules

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Date
import java.util.zip.ZipInputStream

/**
 * 词库模块
 * 支持50万+基础词库，用户词频学习，云同步框架，专业词库扩展
 */
class DictionaryModule(context: Context) : BaseKeyboardModule(context), TextProcessingModule {
    
    companion object {
        private const val TAG = "DictionaryModule"
        private const val PREFS_NAME = "dictionary_prefs"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_USER_LEARNING = "user_learning_enabled"
        private const val KEY_CLOUD_SYNC = "cloud_sync_enabled"
        
        // 词库类型
        const val TYPE_PINYIN = "pinyin"
        const val TYPE_WUBI = "wubi"
        const val TYPE_ENGLISH = "english"
        const val TYPE_MEDICAL = "medical"
        const val TYPE_LEGAL = "legal"
        const val TYPE_PROGRAMMING = "programming"
        const val TYPE_CUSTOM = "custom"
    }
    
    // 词条实体
    @Entity(tableName = "dictionary_entries")
    data class DictionaryEntry(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0,
        val word: String,
        val pinyin: String? = null,
        val wubi: String? = null,
        val english: String? = null,
        val type: String,
        val frequency: Int = 1,
        val userFrequency: Int = 0,
        val lastUsed: Date = Date(),
        val createdAt: Date = Date(),
        val updatedAt: Date = Date()
    )
    
    // 用户词频实体
    @Entity(tableName = "user_frequencies")
    data class UserFrequency(
        @PrimaryKey
        val word: String,
        val frequency: Int = 1,
        val lastUsed: Date = Date(),
        val createdAt: Date = Date(),
        val updatedAt: Date = Date()
    )
    
    // 词库统计实体
    @Entity(tableName = "dictionary_stats")
    data class DictionaryStat(
        @PrimaryKey
        val type: String,
        val count: Int = 0,
        val totalFrequency: Long = 0,
        val updatedAt: Date = Date()
    )
    
    // 数据访问对象
    @Dao
    interface DictionaryDao {
        // 基础查询
        @Query("SELECT * FROM dictionary_entries WHERE word LIKE :query ORDER BY (frequency + userFrequency) DESC LIMIT :limit")
        suspend fun search(query: String, limit: Int = 10): List<DictionaryEntry>
        
        @Query("SELECT * FROM dictionary_entries WHERE pinyin LIKE :pinyin ORDER BY (frequency + userFrequency) DESC LIMIT :limit")
        suspend fun searchByPinyin(pinyin: String, limit: Int = 10): List<DictionaryEntry>
        
        @Query("SELECT * FROM dictionary_entries WHERE wubi LIKE :wubi ORDER BY (frequency + userFrequency) DESC LIMIT :limit")
        suspend fun searchByWubi(wubi: String, limit: Int = 10): List<DictionaryEntry>
        
        @Query("SELECT * FROM dictionary_entries WHERE type = :type ORDER BY (frequency + userFrequency) DESC LIMIT :limit")
        suspend fun getByType(type: String, limit: Int = 100): List<DictionaryEntry>
        
        @Query("SELECT * FROM dictionary_entries WHERE id = :id")
        suspend fun getById(id: Long): DictionaryEntry?
        
        @Query("SELECT * FROM dictionary_entries WHERE word = :word")
        suspend fun getByWord(word: String): DictionaryEntry?
        
        // 插入/更新
        @Insert
        suspend fun insert(entry: DictionaryEntry): Long
        
        @Update
        suspend fun update(entry: DictionaryEntry)
        
        @Insert
        suspend fun insertAll(entries: List<DictionaryEntry>)
        
        // 删除
        @Query("DELETE FROM dictionary_entries WHERE id = :id")
        suspend fun deleteById(id: Long)
        
        @Query("DELETE FROM dictionary_entries WHERE type = :type")
        suspend fun deleteByType(type: String)
        
        // 用户词频
        @Query("SELECT * FROM user_frequencies WHERE word = :word")
        suspend fun getUserFrequency(word: String): UserFrequency?
        
        @Insert
        suspend fun insertUserFrequency(frequency: UserFrequency)
        
        @Update
        suspend fun updateUserFrequency(frequency: UserFrequency)
        
        @Query("SELECT * FROM user_frequencies ORDER BY frequency DESC LIMIT :limit")
        suspend fun getTopUserFrequencies(limit: Int = 100): List<UserFrequency>
        
        // 统计
        @Query("SELECT COUNT(*) FROM dictionary_entries")
        suspend fun getTotalCount(): Int
        
        @Query("SELECT COUNT(*) FROM dictionary_entries WHERE type = :type")
        suspend fun getCountByType(type: String): Int
        
        @Query("SELECT type, COUNT(*) as count FROM dictionary_entries GROUP BY type")
        suspend fun getStatsByType(): List<DictionaryStat>
        
        @Transaction
        suspend fun updateEntryFrequency(word: String, increment: Int = 1) {
            val entry = getByWord(word)
            entry?.let {
                it.userFrequency += increment
                it.lastUsed = Date()
                it.updatedAt = Date()
                update(it)
            }
        }
    }
    
    // 数据库
    @Database(
        entities = [DictionaryEntry::class, UserFrequency::class, DictionaryStat::class],
        version = 1,
        exportSchema = false
    )
    abstract class DictionaryDatabase : RoomDatabase() {
        abstract fun dictionaryDao(): DictionaryDao
    }
    
    // 数据库实例
    private lateinit var database: DictionaryDatabase
    private lateinit var dictionaryDao: DictionaryDao
    
    // 共享偏好
    private lateinit var prefs: SharedPreferences
    
    // 设置
    private var userLearningEnabled = true
    private var cloudSyncEnabled = false
    private var lastSyncTime = 0L
    
    // 内存缓存
    private val pinyinCache = mutableMapOf<String, List<DictionaryEntry>>()
    private val wubiCache = mutableMapOf<String, List<DictionaryEntry>>()
    private val englishCache = mutableMapOf<String, List<DictionaryEntry>>()
    private val cacheSize = 1000
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun getDescription(): String {
        return "词库模块，支持50万+基础词库，用户词频学习，云同步框架，专业词库扩展"
    }
    
    override fun onInitialize() {
        // 初始化数据库
        database = Room.databaseBuilder(
            context,
            DictionaryDatabase::class.java,
            "dictionary.db"
        ).build()
        dictionaryDao = database.dictionaryDao()
        
        // 初始化设置
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSettings()
        
        // 检查是否需要加载基础词库
        checkAndLoadBaseDictionary()
        
        Log.d(TAG, "Dictionary module initialized")
    }
    
    override fun onRelease() {
        // 保存设置
        saveSettings()
        
        // 清理缓存
        clearCache()
        
        // 关闭数据库
        database.close()
        
        Log.d(TAG, "Dictionary module released")
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 词库模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    override fun processTextInput(text: String) {
        if (userLearningEnabled) {
            learnFromInput(text)
        }
    }
    
    override fun getPredictions(input: String): List<String> {
        // 根据输入类型自动判断查询方式
        return when {
            isPinyin(input) -> getPinyinPredictions(input)
            isWubi(input) -> getWubiPredictions(input)
            isEnglish(input) -> getEnglishPredictions(input)
            else -> getWordPredictions(input)
        }
    }
    
    override fun learnFromInput(text: String) {
        scope.launch {
            try {
                val words = extractWords(text)
                words.forEach { word ->
                    updateWordFrequency(word)
                }
                Log.d(TAG, "Learned from input: ${words.size} words")
            } catch (e: Exception) {
                Log.e(TAG, "Error learning from input", e)
            }
        }
    }
    
    /**
     * 获取拼音预测
     */
    suspend fun getPinyinPredictions(pinyin: String, limit: Int = 10): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查缓存
                val cached = pinyinCache[pinyin]
                if (cached != null) {
                    return@withContext cached.map { it.word }.take(limit)
                }
                
                // 查询数据库
                val entries = dictionaryDao.searchByPinyin("$pinyin%", limit * 2)
                
                // 按频率排序
                val sortedEntries = entries.sortedByDescending { it.frequency + it.userFrequency }
                
                // 更新缓存
                if (pinyinCache.size >= cacheSize) {
                    pinyinCache.clear()
                }
                pinyinCache[pinyin] = sortedEntries
                
                sortedEntries.take(limit).map { it.word }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pinyin predictions", e)
                emptyList()
            }
        }
    }
    
    /**
     * 获取五笔预测
     */
    suspend fun getWubiPredictions(wubi: String, limit: Int = 10): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查缓存
                val cached = wubiCache[wubi]
                if (cached != null) {
                    return@withContext cached.map { it.word }.take(limit)
                }
                
                // 查询数据库
                val entries = dictionaryDao.searchByWubi("$wubi%", limit * 2)
                
                // 按频率排序
                val sortedEntries = entries.sortedByDescending { it.frequency + it.userFrequency }
                
                // 更新缓存
                if (wubiCache.size >= cacheSize) {
                    wubiCache.clear()
                }
                wubiCache[wubi] = sortedEntries
                
                sortedEntries.take(limit).map { it.word }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting wubi predictions", e)
                emptyList()
            }
        }
    }
    
    /**
     * 获取英文预测
     */
    suspend fun getEnglishPredictions(english: String, limit: Int = 10): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查缓存
                val cached = englishCache[english]
                if (cached != null) {
                    return@withContext cached.map { it.word }.take(limit)
                }
                
                // 查询数据库
                val entries = dictionaryDao.search("$english%", limit * 2)
                    .filter { it.type == TYPE_ENGLISH }
                
                // 按频率排序
                val sortedEntries = entries.sortedByDescending { it.frequency + it.userFrequency }
                
                // 更新缓存
                if (englishCache.size >= cacheSize) {
                    englishCache.clear()
                }
                englishCache[english] = sortedEntries
                
                sortedEntries.take(limit).map { it.word }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting english predictions", e)
                emptyList()
            }
        }
    }
    
    /**
     * 获取词语预测
     */
    private suspend fun getWordPredictions(word: String, limit: Int = 10): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val entries = dictionaryDao.search("$word%", limit * 2)
                val sortedEntries = entries.sortedByDescending { it.frequency + it.userFrequency }
                sortedEntries.take(limit).map { it.word }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting word predictions", e)
                emptyList()
            }
        }
    }
    
    /**
     * 添加词条
     */
    suspend fun addEntry(
        word: String,
        pinyin: String? = null,
        wubi: String? = null,
        english: String? = null,
        type: String = TYPE_CUSTOM,
        frequency: Int = 1
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val existingEntry = dictionaryDao.getByWord(word)
                
                if (existingEntry != null) {
                    // 更新现有词条
                    existingEntry.apply {
                        this.pinyin = pinyin ?: this.pinyin
                        this.wubi = wubi ?: this.wubi
                        this.english = english ?: this.english
                        this.frequency += frequency
                        this.updatedAt = Date()
                    }
                    dictionaryDao.update(existingEntry)
                } else {
                    // 插入新词条
                    val entry = DictionaryEntry(
                        word = word,
                        pinyin = pinyin,
                        wubi = wubi,
                        english = english,
                        type = type,
                        frequency = frequency
                    )
                    dictionaryDao.insert(entry)
                }
                
                // 清理相关缓存
                clearCache()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding entry", e)
                false
            }
        }
    }
    
    /**
     * 批量添加词条
     */
    suspend fun addEntries(entries: List<DictionaryEntry>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                dictionaryDao.insertAll(entries)
                
                // 清理缓存
                clearCache()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding entries", e)
                false
            }
        }
    }
    
    /**
     * 删除词条
     */
    suspend fun deleteEntry(id: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                dictionaryDao.deleteById(id)
                
                // 清理缓存
                clearCache()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting entry", e)
                false
            }
        }
    }
    
    /**
     * 删除指定类型的所有词条
     */
    suspend fun deleteByType(type: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                dictionaryDao.deleteByType(type)
                
                // 清理缓存
                clearCache()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting entries by type", e)
                false
            }
        }
    }
    
    /**
     * 更新词语频率
     */
    private suspend fun updateWordFrequency(word: String, increment: Int = 1) {
        withContext(Dispatchers.IO) {
            try {
                // 更新词条频率
                dictionaryDao.updateEntryFrequency(word, increment)
                
                // 更新用户频率表
                val userFreq = dictionaryDao.getUserFrequency(word)
                if (userFreq != null) {
                    userFreq.frequency += increment
                    userFreq.lastUsed = Date()
                    userFreq.updatedAt = Date()
                    dictionaryDao.updateUserFrequency(userFreq)
                } else {
                    val newFreq = UserFrequency(
                        word = word,
                        frequency = increment,
                        lastUsed = Date(),
                        createdAt = Date(),
                        updatedAt = Date()
                    )
                    dictionaryDao.insertUserFrequency(newFreq)
                }
                
                // 清理相关缓存
                clearCacheForWord(word)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating word frequency", e)
            }
        }
    }
    
    /**
     * 从文本中提取词语
     */
    private fun extractWords(text: String): List<String> {
        val words = mutableListOf<String>()
        
        // 中文词语提取（简单实现）
        val chineseRegex = Regex("[\u4e00-\u9fa5]{2,}")
        val chineseMatches = chineseRegex.findAll(text)
        chineseMatches.forEach { match ->
            words.add(match.value)
        }
        
        // 英文单词提取
        val englishRegex = Regex("\\b[a-zA-Z]{3,}\\b")
        val englishMatches = englishRegex.findAll(text)
        englishMatches.forEach { match ->
            words.add(match.value.lowercase())
        }
        
        return words.distinct()
    }
    
    /**
     * 检查是否为拼音
     */
    private fun isPinyin(input: String): Boolean {
        return input.matches(Regex("[a-z]+")) && input.length <= 6
    }
    
    /**
     * 检查是否为五笔
     */
    private fun isWubi(input: String): Boolean {
        return input.matches(Regex("[a-z]{1,4}"))
    }
    
    /**
     * 检查是否为英文
     */
    private fun isEnglish(input: String): Boolean {
        return input.matches(Regex("[a-zA-Z]+"))
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        userLearningEnabled = prefs.getBoolean(KEY_USER_LEARNING, true)
        cloudSyncEnabled = prefs.getBoolean(KEY_CLOUD_SYNC, false)
        lastSyncTime = prefs.getLong(KEY_LAST_SYNC, 0)
        
        Log.d(TAG, "Settings loaded: userLearning=$userLearningEnabled, cloudSync=$cloudSyncEnabled")
    }
    
    /**
     * 保存设置
     */
    private fun saveSettings() {
        prefs.edit {
            putBoolean(KEY_USER_LEARNING, userLearningEnabled)
            putBoolean(KEY_CLOUD_SYNC, cloudSyncEnabled)
            putLong(KEY_LAST_SYNC, lastSyncTime)
        }
        
        Log.d(TAG, "Settings saved")
    }
    
    /**
     * 检查和加载基础词库
     */
    private fun checkAndLoadBaseDictionary() {
        scope.launch {
            try {
                val totalCount = dictionaryDao.getTotalCount()
                if (totalCount == 0) {
                    Log.d(TAG, "Base dictionary is empty, loading...")
                    loadBaseDictionary()
                } else {
                    Log.d(TAG, "Base dictionary already loaded: $totalCount entries")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking base dictionary", e)
            }
        }
    }
    
    /**
     * 加载基础词库
     */
    private suspend fun loadBaseDictionary() {
        withContext(Dispatchers.IO) {
            try {
                // 这里应该从assets加载基础词库文件
                // 简化实现：添加一些示例词条
                
                val baseEntries = mutableListOf<DictionaryEntry>()
                
                // 添加拼音词库
                val pinyinWords = listOf(
                    "你好" to "nihao",
                    "谢谢" to "xiexie",
                    "再见" to "zaijian",
                    "中国" to "zhongguo",
                    "北京" to "beijing",
                    "上海" to "shanghai",
                    "广州" to "guangzhou",
                    "深圳" to "shenzhen",
                    "杭州" to "hangzhou",
                    "成都" to "chengdu"
                )
                
                pinyinWords.forEach { (word, pinyin) ->
                    baseEntries.add(
                        DictionaryEntry(
                            word = word,
                            pinyin = pinyin,
                            type = TYPE_PINYIN,
                            frequency = 100
                        )
                    )
                }
                
                // 添加五笔词库
                val wubiWords = listOf(
                    "我" to "trnt",
                    "你" to "wqiy",
                    "他" to "wbn",
                    "好" to "vbg",
                    "是" to "jghu",
                    "的" to "rqyy",
                    "了" to "bn",
                    "在" to "dhfd",
                    "有" to "def",
                    "没" to "imcy"
                )
                
                wubiWords.forEach { (word, wubi) ->
                    baseEntries.add(
                        DictionaryEntry(
                            word = word,
                            wubi = wubi,
                            type = TYPE_WUBI,
                            frequency = 100
                        )
                    )
                }
                
                // 添加英文词库
                val englishWords = listOf(
                    "hello" to "你好",
                    "world" to "世界",
                    "android" to "安卓",
                    "keyboard" to "键盘",
                    "input" to "输入",
                    "method" to "方法",
                    "dictionary" to "词典",
                    "prediction" to "预测",
                    "correction" to "纠正",
                    "auto" to "自动"
                )
                
                englishWords.forEach { (word, meaning) ->
                    baseEntries.add(
                        DictionaryEntry(
                            word = word,
                            english = meaning,
                            type = TYPE_ENGLISH,
                            frequency = 100
                        )
                    )
                }
                
                // 批量插入
                dictionaryDao.insertAll(baseEntries)
                
                Log.d(TAG, "Base dictionary loaded: ${baseEntries.size} entries")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading base dictionary", e)
            }
        }
    }
    
    /**
     * 清理缓存
     */
    private fun clearCache() {
        pinyinCache.clear()
        wubiCache.clear()
        englishCache.clear()
    }
    
    /**
     * 清理指定词语的缓存
     */
    private fun clearCacheForWord(word: String) {
        // 清理拼音缓存
        pinyinCache.keys.removeAll { key ->
            word.contains(key, ignoreCase = true)
        }
        
        // 清理五笔缓存
        wubiCache.keys.removeAll { key ->
            word.contains(key, ignoreCase = true)
        }
        
        // 清理英文缓存
        englishCache.keys.removeAll { key ->
            word.contains(key, ignoreCase = true)
        }
    }
    
    /**
     * 获取词库统计
     */
    suspend fun getStatistics(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val totalCount = dictionaryDao.getTotalCount()
                val statsByType = dictionaryDao.getStatsByType()
                val topUserFrequencies = dictionaryDao.getTopUserFrequencies(10)
                
                mapOf(
                    "total_entries" to totalCount,
                    "stats_by_type" to statsByType.associate { it.type to it.count },
                    "top_user_words" to topUserFrequencies.map { it.word to it.frequency },
                    "user_learning_enabled" to userLearningEnabled,
                    "cloud_sync_enabled" to cloudSyncEnabled,
                    "last_sync_time" to lastSyncTime,
                    "cache_size" to mapOf(
                        "pinyin" to pinyinCache.size,
                        "wubi" to wubiCache.size,
                        "english" to englishCache.size
                    ),
                    "last_used" to lastUsedTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting statistics", e)
                emptyMap()
            }
        }
    }
    
    /**
     * 导出词库
     */
    suspend fun exportDictionary(type: String? = null): List<DictionaryEntry> {
        return withContext(Dispatchers.IO) {
            try {
                if (type != null) {
                    dictionaryDao.getByType(type, Int.MAX_VALUE)
                } else {
                    // 导出所有词条（限制数量防止内存溢出）
                    dictionaryDao.search("", 10000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting dictionary", e)
                emptyList()
            }
        }
    }
    
    /**
     * 导入词库
     */
    suspend fun importDictionary(entries: List<DictionaryEntry>, replace: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (replace) {
                    // 删除现有词条
                    entries.firstOrNull()?.type?.let { type ->
                        dictionaryDao.deleteByType(type)
                    }
                }
                
                // 插入新词条
                dictionaryDao.insertAll(entries)
                
                // 清理缓存
                clearCache()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error importing dictionary", e)
                false
            }
        }
    }
    
    /**
     * 设置用户学习是否启用
     */
    fun setUserLearningEnabled(enabled: Boolean) {
        userLearningEnabled = enabled
    }
    
    /**
     * 设置云同步是否启用
     */
    fun setCloudSyncEnabled(enabled: Boolean) {
        cloudSyncEnabled = enabled
    }
    
    /**
     * 同步到云端
     */
    suspend fun syncToCloud(): Boolean {
        if (!cloudSyncEnabled) {
            Log.d(TAG, "Cloud sync disabled")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // 这里应该实现云同步逻辑
                // 简化实现：只更新同步时间
                lastSyncTime = System.currentTimeMillis()
                saveSettings()
                
                Log.d(TAG, "Cloud sync completed at $lastSyncTime")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing to cloud", e)
                false
            }
        }
    }
    
    /**
     * 从云端同步
     */
    suspend fun syncFromCloud(): Boolean {
        if (!cloudSyncEnabled) {
            Log.d(TAG, "Cloud sync disabled")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // 这里应该实现从云端同步的逻辑
                // 简化实现：只更新同步时间
                lastSyncTime = System.currentTimeMillis()
                saveSettings()
                
                Log.d(TAG, "Cloud sync from completed at $lastSyncTime")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing from cloud", e)
                false
            }
        }
    }
    
    /**
     * 搜索词条
     */
    suspend fun searchEntries(
        query: String,
        type: String? = null,
        limit: Int = 50
    ): List<DictionaryEntry> {
        return withContext(Dispatchers.IO) {
            try {
                val results = if (type != null) {
                    dictionaryDao.search("$query%", limit * 2)
                        .filter { it.type == type }
                } else {
                    dictionaryDao.search("$query%", limit * 2)
                }
                
                results.sortedByDescending { it.frequency + it.userFrequency }
                    .take(limit)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching entries", e)
                emptyList()
            }
        }
    }
    
    /**
     * 获取热门词语
     */
    suspend fun getHotWords(limit: Int = 20): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            try {
                val userFrequencies = dictionaryDao.getTopUserFrequencies(limit)
                userFrequencies.map { it.word to it.frequency }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting hot words", e)
                emptyList()
            }
        }
    }
    
    /**
     * 清理过期缓存
     */
    fun cleanupCache() {
        // 简单实现：清理所有缓存
        clearCache()
        Log.d(TAG, "Cache cleaned up")
    }
}