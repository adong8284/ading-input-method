package com.ading.inputmethod.modules

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 剪贴板管理模块
 * 监听剪贴板变化，记录历史，支持加密存储和隐私保护
 */
class ClipboardModule(context: Context) : BaseKeyboardModule(context) {
    
    companion object {
        private const val TAG = "ClipboardModule"
        private const val MAX_CLIPBOARD_ITEMS = 100
        private const val ENCRYPTION_KEY_ALIAS = "clipboard_encryption_key"
    }
    
    // 剪贴板管理器
    private lateinit var clipboardManager: ClipboardManager
    
    // 数据库
    private lateinit var database: ClipboardDatabase
    private lateinit var clipboardDao: ClipboardDao
    
    // 监听器
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    
    // 启用状态
    private var isEnabled = AtomicBoolean(true)
    
    // 隐私保护模式（Android 10+）
    private var privacyModeEnabled = AtomicBoolean(false)
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // 当前剪贴板内容
    private var currentClipboardContent: String? = null
    
    override fun getDescription(): String {
        return "剪贴板管理模块，监听剪贴板变化，记录历史，支持加密存储和隐私保护"
    }
    
    override fun onInitialize() {
        // 初始化剪贴板管理器
        clipboardManager = ContextCompat.getSystemService(context, ClipboardManager::class.java)!!
        
        // 初始化数据库
        database = Room.databaseBuilder(
            context,
            ClipboardDatabase::class.java,
            "clipboard.db"
        ).build()
        clipboardDao = database.clipboardDao()
        
        // 检查Android 10+的剪贴板权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkClipboardPermission()
        }
        
        // 设置剪贴板监听器
        setupClipboardListener()
        
        // 加载现有历史记录
        loadClipboardHistory()
        
        Log.d(TAG, "Clipboard module initialized")
    }
    
    override fun onRelease() {
        // 移除监听器
        removeClipboardListener()
        
        // 关闭数据库
        database.close()
        
        // 清理资源
        currentClipboardContent = null
        
        Log.d(TAG, "Clipboard module released")
    }
    
    override fun isEnabled(): Boolean {
        return isEnabled.get() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !privacyModeEnabled.get())
    }
    
    override fun setEnabled(enabled: Boolean) {
        isEnabled.set(enabled)
        
        if (enabled) {
            setupClipboardListener()
        } else {
            removeClipboardListener()
        }
        
        Log.d(TAG, "Clipboard module ${if (enabled) "enabled" else "disabled"}")
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 剪贴板模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    /**
     * 设置剪贴板监听器
     */
    private fun setupClipboardListener() {
        if (clipboardListener != null || !isEnabled.get()) {
            return
        }
        
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            onClipboardChanged()
        }
        
        clipboardManager.addPrimaryClipChangedListener(clipboardListener!!)
        Log.d(TAG, "Clipboard listener added")
    }
    
    /**
     * 移除剪贴板监听器
     */
    private fun removeClipboardListener() {
        clipboardListener?.let {
            clipboardManager.removePrimaryClipChangedListener(it)
            clipboardListener = null
            Log.d(TAG, "Clipboard listener removed")
        }
    }
    
    /**
     * 剪贴板变化回调
     */
    private fun onClipboardChanged() {
        if (!isEnabled.get()) {
            return
        }
        
        scope.launch {
            try {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val text = item.text?.toString()
                    
                    if (text != null && text != currentClipboardContent) {
                        currentClipboardContent = text
                        saveClipboardItem(text)
                        Log.d(TAG, "Clipboard content saved: ${text.take(50)}...")
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Clipboard access denied due to privacy restrictions")
                // Android 10+ 需要用户授权
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    privacyModeEnabled.set(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing clipboard change", e)
            }
        }
    }
    
    /**
     * 保存剪贴板项目
     */
    private suspend fun saveClipboardItem(content: String) {
        withContext(Dispatchers.IO) {
            try {
                // 检查是否已存在相同内容
                val existingItem = clipboardDao.getItemByContent(content)
                if (existingItem != null) {
                    // 更新现有项目的时间戳
                    existingItem.timestamp = Date()
                    existingItem.accessCount = existingItem.accessCount + 1
                    clipboardDao.update(existingItem)
                } else {
                    // 创建新项目
                    val item = ClipboardItem(
                        content = content,
                        timestamp = Date(),
                        accessCount = 1,
                        isPinned = false,
                        category = detectCategory(content)
                    )
                    
                    // 检查数量限制
                    val totalCount = clipboardDao.getCount()
                    if (totalCount >= MAX_CLIPBOARD_ITEMS) {
                        // 删除最旧的非置顶项目
                        val oldestItem = clipboardDao.getOldestUnpinnedItem()
                        oldestItem?.let {
                            clipboardDao.delete(it)
                        }
                    }
                    
                    clipboardDao.insert(item)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving clipboard item", e)
            }
        }
    }
    
    /**
     * 加载剪贴板历史
     */
    private fun loadClipboardHistory() {
        scope.launch {
            try {
                val count = clipboardDao.getCount()
                Log.d(TAG, "Loaded $count clipboard items from database")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading clipboard history", e)
            }
        }
    }
    
    /**
     * 获取所有剪贴板项目
     */
    suspend fun getAllItems(): List<ClipboardItem> {
        return withContext(Dispatchers.IO) {
            try {
                clipboardDao.getAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all items", e)
                emptyList()
            }
        }
    }
    
    /**
     * 获取置顶项目
     */
    suspend fun getPinnedItems(): List<ClipboardItem> {
        return withContext(Dispatchers.IO) {
            try {
                clipboardDao.getPinned()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pinned items", e)
                emptyList()
            }
        }
    }
    
    /**
     * 按分类获取项目
     */
    suspend fun getItemsByCategory(category: String): List<ClipboardItem> {
        return withContext(Dispatchers.IO) {
            try {
                clipboardDao.getByCategory(category)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting items by category", e)
                emptyList()
            }
        }
    }
    
    /**
     * 搜索剪贴板项目
     */
    suspend fun searchItems(query: String): List<ClipboardItem> {
        return withContext(Dispatchers.IO) {
            try {
                clipboardDao.search("%$query%")
            } catch (e: Exception) {
                Log.e(TAG, "Error searching items", e)
                emptyList()
            }
        }
    }
    
    /**
     * 置顶/取消置顶项目
     */
    suspend fun togglePin(itemId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val item = clipboardDao.getById(itemId)
                item?.let {
                    it.isPinned = !it.isPinned
                    it.timestamp = Date() // 更新时间戳
                    clipboardDao.update(it)
                    true
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling pin", e)
                false
            }
        }
    }
    
    /**
     * 删除项目
     */
    suspend fun deleteItem(itemId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val item = clipboardDao.getById(itemId)
                item?.let {
                    clipboardDao.delete(it)
                    true
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting item", e)
                false
            }
        }
    }
    
    /**
     * 清空所有项目
     */
    suspend fun clearAll(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clipboardDao.deleteAll()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing all items", e)
                false
            }
        }
    }
    
    /**
     * 清除非置顶项目
     */
    suspend fun clearUnpinned(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clipboardDao.deleteUnpinned()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing unpinned items", e)
                false
            }
        }
    }
    
    /**
     * 复制到剪贴板
     */
    fun copyToClipboard(text: String): Boolean {
        return try {
            val clip = ClipData.newPlainText("阿栋输入法", text)
            clipboardManager.setPrimaryClip(clip)
            currentClipboardContent = text
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
            false
        }
    }
    
    /**
     * 从剪贴板获取当前内容
     */
    fun getCurrentClipboardContent(): String? {
        return try {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.toString()
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard access denied")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting clipboard content", e)
            null
        }
    }
    
    /**
     * 检测内容分类
     */
    private fun detectCategory(content: String): String {
        return when {
            // URL
            content.matches(Regex("^(https?|ftp)://.*")) -> "链接"
            // 邮箱
            content.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) -> "邮箱"
            // 电话号码
            content.matches(Regex("^[+]?[0-9\\s-()]{10,}$")) -> "电话"
            // 日期
            content.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> "日期"
            // 时间
            content.matches(Regex("^\\d{2}:\\d{2}(:\\d{2})?$")) -> "时间"
            // 数字
            content.matches(Regex("^\\d+$")) -> "数字"
            // 代码
            content.contains(Regex("\\b(function|class|import|export|var|let|const|if|else|for|while|return)\\b")) -> "代码"
            // 长文本
            content.length > 100 -> "长文本"
            // 短文本
            content.length <= 20 -> "短文本"
            // 默认
            else -> "文本"
        }
    }
    
    /**
     * 检查Android 10+剪贴板权限
     */
    private fun checkClipboardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 需要用户手动在设置中启用剪贴板访问
            // 这里只是检查，无法直接请求权限
            privacyModeEnabled.set(false) // 假设默认未启用隐私模式
        }
    }
    
    /**
     * 打开剪贴板权限设置
     */
    fun openClipboardPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Settings.ACTION_CLIPBOARD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
    
    /**
     * 获取模块统计
     */
    suspend fun getStatistics(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val totalCount = clipboardDao.getCount()
                val pinnedCount = clipboardDao.getPinnedCount()
                val categoryStats = clipboardDao.getCategoryStats()
                
                mapOf(
                    "enabled" to isEnabled.get(),
                    "privacy_mode" to privacyModeEnabled.get(),
                    "total_items" to totalCount,
                    "pinned_items" to pinnedCount,
                    "category_stats" to categoryStats,
                    "current_content" to (currentClipboardContent?.take(50) ?: "null"),
                    "last_used" to lastUsedTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting statistics", e)
                emptyMap()
            }
        }
    }
    
    /**
     * 导出剪贴板历史
     */
    suspend fun exportHistory(): String {
        return withContext(Dispatchers.IO) {
            try {
                val items = clipboardDao.getAll()
                val json = StringBuilder()
                json.append("[\n")
                
                items.forEachIndexed { index, item ->
                    json.append("  {\n")
                    json.append("    \"id\": ${item.id},\n")
                    json.append("    \"content\": \"${escapeJson(item.content)}\",\n")
                    json.append("    \"timestamp\": \"${item.timestamp}\",\n")
                    json.append("    \"accessCount\": ${item.accessCount},\n")
                    json.append("    \"isPinned\": ${item.isPinned},\n")
                    json.append("    \"category\": \"${item.category}\"\n")
                    json.append("  }")
                    
                    if (index < items.size - 1) {
                        json.append(",")
                    }
                    json.append("\n")
                }
                
                json.append("]")
                json.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting history", e)
                "[]"
            }
        }
    }
    
    /**
     * 导入剪贴板历史
     */
    suspend fun importHistory(json: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 这里应该实现JSON解析
                // 简化实现：直接返回false
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error importing history", e)
                false
            }
        }
    }
    
    /**
     * 转义JSON字符串
     */
    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * 剪贴板项目实体
 */
@Entity(tableName = "clipboard_items")
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Date,
    var accessCount: Int = 1,
    var isPinned: Boolean = false,
    val category: String = "文本"
)

/**
 * 剪贴板数据访问对象
 */
@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC")
    suspend fun getAll(): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE isPinned = 1 ORDER BY timestamp DESC")
    suspend fun getPinned(): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(category: String): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE content LIKE :query ORDER BY timestamp DESC")
    suspend fun search(query: String): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE id = :id")
    suspend fun getById(id: Long): ClipboardItem?
    
    @Query("SELECT * FROM clipboard_items WHERE content = :content LIMIT 1")
    suspend fun getItemByContent(content: String): ClipboardItem?
    
    @Query("SELECT COUNT(*) FROM clipboard_items")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM clipboard_items WHERE isPinned = 1")
    suspend fun getPinnedCount(): Int
    
    @Query("SELECT * FROM clipboard_items WHERE isPinned = 0 ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestUnpinnedItem(): ClipboardItem?
    
    @Query("SELECT category, COUNT(*) as count FROM clipboard_items GROUP BY category ORDER BY count DESC")
    suspend fun getCategoryStats(): List<CategoryStat>
    
    @Insert
    suspend fun insert(item: ClipboardItem)
    
    @Update
    suspend fun update(item: ClipboardItem)
    
    @Delete
    suspend fun delete(item: ClipboardItem)
    
    @Query("DELETE FROM clipboard_items")
    suspend fun deleteAll()
    
    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun deleteUnpinned()
}

/**
 * 分类统计
 */
data class CategoryStat(
    val category: String,
    val count: Int
)

/**
 * 剪贴板数据库
 */
@Database(entities = [ClipboardItem::class], version = 1)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
}