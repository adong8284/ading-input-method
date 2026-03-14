package com.ading.inputmethod.modules

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主题模块
 * 支持5套内置主题和用户自定义主题，支持背景图、按键色、字体、透明度配置
 */
class ThemeModule(context: Context) : BaseKeyboardModule(context) {
    
    companion object {
        private const val TAG = "ThemeModule"
        private const val PREFS_NAME = "theme_preferences"
        private const val KEY_CURRENT_THEME = "current_theme"
        private const val KEY_CUSTOM_THEMES = "custom_themes"
        private const val THEMES_DIR = "themes"
        
        // 内置主题ID
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_EYE_PROTECTION = "eye_protection"
        const val THEME_CYBERPUNK = "cyberpunk"
        const val THEME_INK_WASH = "ink_wash"
    }
    
    // 主题数据类
    data class ThemeConfig(
        val id: String,
        val name: String,
        val backgroundColor: Int,
        val keyColor: Int,
        val keyPressedColor: Int,
        val keyTextColor: Int,
        val keyBorderColor: Int,
        val functionKeyColor: Int,
        val spaceKeyColor: Int,
        val keyRadius: Float,
        val keyTextSize: Float,
        val backgroundImage: String? = null,
        val backgroundOpacity: Float = 1.0f,
        val isCustom: Boolean = false,
        val createdTime: Long = System.currentTimeMillis()
    )
    
    // 当前主题
    private var currentTheme: ThemeConfig = createLightTheme()
    
    // 自定义主题列表
    private val customThemes = mutableListOf<ThemeConfig>()
    
    // SharedPreferences
    private lateinit var prefs: SharedPreferences
    
    // 主题文件目录
    private lateinit var themesDir: File
    
    override fun getDescription(): String {
        return "主题模块，支持5套内置主题和用户自定义主题，支持背景图、按键色、字体、透明度配置"
    }
    
    override fun onInitialize() {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themesDir = File(context.filesDir, THEMES_DIR)
        
        // 确保主题目录存在
        if (!themesDir.exists()) {
            themesDir.mkdirs()
        }
        
        // 加载当前主题
        loadCurrentTheme()
        
        // 加载自定义主题
        loadCustomThemes()
        
        Log.d(TAG, "Theme module initialized, current theme: ${currentTheme.name}")
    }
    
    override fun onRelease() {
        // 保存当前主题
        saveCurrentTheme()
        
        // 保存自定义主题
        saveCustomThemes()
        
        Log.d(TAG, "Theme module released")
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 主题模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    /**
     * 获取当前主题
     */
    fun getCurrentTheme(): ThemeConfig {
        return currentTheme
    }
    
    /**
     * 设置当前主题
     */
    fun setCurrentTheme(themeId: String): Boolean {
        return when (themeId) {
            THEME_LIGHT -> {
                currentTheme = createLightTheme()
                saveCurrentTheme()
                true
            }
            THEME_DARK -> {
                currentTheme = createDarkTheme()
                saveCurrentTheme()
                true
            }
            THEME_EYE_PROTECTION -> {
                currentTheme = createEyeProtectionTheme()
                saveCurrentTheme()
                true
            }
            THEME_CYBERPUNK -> {
                currentTheme = createCyberpunkTheme()
                saveCurrentTheme()
                true
            }
            THEME_INK_WASH -> {
                currentTheme = createInkWashTheme()
                saveCurrentTheme()
                true
            }
            else -> {
                // 查找自定义主题
                val customTheme = customThemes.find { it.id == themeId }
                customTheme?.let {
                    currentTheme = it
                    saveCurrentTheme()
                    true
                } ?: false
            }
        }
    }
    
    /**
     * 获取所有内置主题
     */
    fun getBuiltinThemes(): List<ThemeConfig> {
        return listOf(
            createLightTheme(),
            createDarkTheme(),
            createEyeProtectionTheme(),
            createCyberpunkTheme(),
            createInkWashTheme()
        )
    }
    
    /**
     * 获取所有自定义主题
     */
    fun getCustomThemes(): List<ThemeConfig> {
        return customThemes.sortedByDescending { it.createdTime }
    }
    
    /**
     * 获取所有主题（内置+自定义）
     */
    fun getAllThemes(): List<ThemeConfig> {
        return getBuiltinThemes() + customThemes
    }
    
    /**
     * 创建自定义主题
     */
    fun createCustomTheme(
        name: String,
        backgroundColor: Int,
        keyColor: Int,
        keyPressedColor: Int,
        keyTextColor: Int,
        keyBorderColor: Int = Color.TRANSPARENT,
        functionKeyColor: Int = keyColor,
        spaceKeyColor: Int = keyColor,
        keyRadius: Float = 8f,
        keyTextSize: Float = 16f,
        backgroundImage: String? = null,
        backgroundOpacity: Float = 1.0f
    ): ThemeConfig {
        val themeId = generateThemeId(name)
        
        val theme = ThemeConfig(
            id = themeId,
            name = name,
            backgroundColor = backgroundColor,
            keyColor = keyColor,
            keyPressedColor = keyPressedColor,
            keyTextColor = keyTextColor,
            keyBorderColor = keyBorderColor,
            functionKeyColor = functionKeyColor,
            spaceKeyColor = spaceKeyColor,
            keyRadius = keyRadius,
            keyTextSize = keyTextSize,
            backgroundImage = backgroundImage,
            backgroundOpacity = backgroundOpacity,
            isCustom = true,
            createdTime = System.currentTimeMillis()
        )
        
        customThemes.add(theme)
        saveCustomThemes()
        
        Log.d(TAG, "Custom theme created: $name ($themeId)")
        return theme
    }
    
    /**
     * 更新自定义主题
     */
    fun updateCustomTheme(
        themeId: String,
        name: String? = null,
        backgroundColor: Int? = null,
        keyColor: Int? = null,
        keyPressedColor: Int? = null,
        keyTextColor: Int? = null,
        keyBorderColor: Int? = null,
        functionKeyColor: Int? = null,
        spaceKeyColor: Int? = null,
        keyRadius: Float? = null,
        keyTextSize: Float? = null,
        backgroundImage: String? = null,
        backgroundOpacity: Float? = null
    ): Boolean {
        val index = customThemes.indexOfFirst { it.id == themeId }
        if (index == -1) return false
        
        val oldTheme = customThemes[index]
        val updatedTheme = oldTheme.copy(
            name = name ?: oldTheme.name,
            backgroundColor = backgroundColor ?: oldTheme.backgroundColor,
            keyColor = keyColor ?: oldTheme.keyColor,
            keyPressedColor = keyPressedColor ?: oldTheme.keyPressedColor,
            keyTextColor = keyTextColor ?: oldTheme.keyTextColor,
            keyBorderColor = keyBorderColor ?: oldTheme.keyBorderColor,
            functionKeyColor = functionKeyColor ?: oldTheme.functionKeyColor,
            spaceKeyColor = spaceKeyColor ?: oldTheme.spaceKeyColor,
            keyRadius = keyRadius ?: oldTheme.keyRadius,
            keyTextSize = keyTextSize ?: oldTheme.keyTextSize,
            backgroundImage = backgroundImage ?: oldTheme.backgroundImage,
            backgroundOpacity = backgroundOpacity ?: oldTheme.backgroundOpacity,
            createdTime = System.currentTimeMillis()
        )
        
        customThemes[index] = updatedTheme
        saveCustomThemes()
        
        // 如果这是当前主题，更新当前主题
        if (currentTheme.id == themeId) {
            currentTheme = updatedTheme
            saveCurrentTheme()
        }
        
        Log.d(TAG, "Custom theme updated: $themeId")
        return true
    }
    
    /**
     * 删除自定义主题
     */
    fun deleteCustomTheme(themeId: String): Boolean {
        val theme = customThemes.find { it.id == themeId } ?: return false
        
        // 删除背景图片文件（如果有）
        theme.backgroundImage?.let { imagePath ->
            val imageFile = File(themesDir, imagePath)
            if (imageFile.exists()) {
                imageFile.delete()
            }
        }
        
        // 从列表中移除
        customThemes.remove(theme)
        saveCustomThemes()
        
        // 如果删除的是当前主题，切换到默认主题
        if (currentTheme.id == themeId) {
            setCurrentTheme(THEME_LIGHT)
        }
        
        Log.d(TAG, "Custom theme deleted: $themeId")
        return true
    }
    
    /**
     * 导出主题配置
     */
    fun exportTheme(themeId: String): String? {
        val theme = if (themeId == currentTheme.id) {
            currentTheme
        } else {
            getAllThemes().find { it.id == themeId }
        }
        
        return theme?.let { themeConfig ->
            val json = JSONObject().apply {
                put("id", themeConfig.id)
                put("name", themeConfig.name)
                put("backgroundColor", themeConfig.backgroundColor)
                put("keyColor", themeConfig.keyColor)
                put("keyPressedColor", themeConfig.keyPressedColor)
                put("keyTextColor", themeConfig.keyTextColor)
                put("keyBorderColor", themeConfig.keyBorderColor)
                put("functionKeyColor", themeConfig.functionKeyColor)
                put("spaceKeyColor", themeConfig.spaceKeyColor)
                put("keyRadius", themeConfig.keyRadius)
                put("keyTextSize", themeConfig.keyTextSize)
                put("backgroundImage", themeConfig.backgroundImage ?: "")
                put("backgroundOpacity", themeConfig.backgroundOpacity)
                put("isCustom", themeConfig.isCustom)
                put("createdTime", themeConfig.createdTime)
            }
            
            json.toString(2)
        }
    }
    
    /**
     * 导入主题配置
     */
    fun importTheme(jsonString: String): ThemeConfig? {
        return try {
            val json = JSONObject(jsonString)
            val themeId = json.getString("id")
            val themeName = json.getString("name")
            
            // 检查是否已存在
            val existingTheme = customThemes.find { it.id == themeId }
            if (existingTheme != null) {
                // 更新现有主题
                updateCustomTheme(
                    themeId = themeId,
                    name = themeName,
                    backgroundColor = json.getInt("backgroundColor"),
                    keyColor = json.getInt("keyColor"),
                    keyPressedColor = json.getInt("keyPressedColor"),
                    keyTextColor = json.getInt("keyTextColor"),
                    keyBorderColor = json.getInt("keyBorderColor"),
                    functionKeyColor = json.getInt("functionKeyColor"),
                    spaceKeyColor = json.getInt("spaceKeyColor"),
                    keyRadius = json.getDouble("keyRadius").toFloat(),
                    keyTextSize = json.getDouble("keyTextSize").toFloat(),
                    backgroundImage = if (json.getString("backgroundImage").isNotEmpty()) 
                        json.getString("backgroundImage") else null,
                    backgroundOpacity = json.getDouble("backgroundOpacity").toFloat()
                )
                
                customThemes.find { it.id == themeId }
            } else {
                // 创建新主题
                createCustomTheme(
                    name = themeName,
                    backgroundColor = json.getInt("backgroundColor"),
                    keyColor = json.getInt("keyColor"),
                    keyPressedColor = json.getInt("keyPressedColor"),
                    keyTextColor = json.getInt("keyTextColor"),
                    keyBorderColor = json.getInt("keyBorderColor"),
                    functionKeyColor = json.getInt("functionKeyColor"),
                    spaceKeyColor = json.getInt("spaceKeyColor"),
                    keyRadius = json.getDouble("keyRadius").toFloat(),
                    keyTextSize = json.getDouble("keyTextSize").toFloat(),
                    backgroundImage = if (json.getString("backgroundImage").isNotEmpty()) 
                        json.getString("backgroundImage") else null,
                    backgroundOpacity = json.getDouble("backgroundOpacity").toFloat()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing theme", e)
            null
        }
    }
    
    /**
     * 设置背景图片
     */
    fun setBackgroundImage(themeId: String, bitmap: Bitmap): String? {
        val theme = if (themeId == currentTheme.id) {
            currentTheme
        } else {
            customThemes.find { it.id == themeId }
        }
        
        theme ?: return null
        
        try {
            // 生成唯一文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "bg_${themeId}_$timestamp.png"
            val imageFile = File(themesDir, fileName)
            
            // 保存图片
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            
            // 删除旧图片（如果有）
            theme.backgroundImage?.let { oldImagePath ->
                val oldImageFile = File(themesDir, oldImagePath)
                if (oldImageFile.exists()) {
                    oldImageFile.delete()
                }
            }
            
            // 更新主题
            updateCustomTheme(
                themeId = themeId,
                backgroundImage = fileName
            )
            
            return fileName
        } catch (e: Exception) {
            Log.e(TAG, "Error saving background image", e)
            return null
        }
    }
    
    /**
     * 获取背景图片
     */
    fun getBackgroundImage(imagePath: String): Bitmap? {
        return try {
            val imageFile = File(themesDir, imagePath)
            if (imageFile.exists()) {
                BitmapFactory.decodeFile(imageFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading background image", e)
            null
        }
    }
    
    /**
     * 获取背景图片Drawable
     */
    fun getBackgroundImageDrawable(imagePath: String): Drawable? {
        return getBackgroundImage(imagePath)?.let { bitmap ->
            BitmapDrawable(context.resources, bitmap)
        }
    }
    
    /**
     * 删除背景图片
     */
    fun deleteBackgroundImage(themeId: String): Boolean {
        val theme = customThemes.find { it.id == themeId } ?: return false
        
        theme.backgroundImage?.let { imagePath ->
            val imageFile = File(themesDir, imagePath)
            if (imageFile.exists()) {
                imageFile.delete()
                
                // 更新主题，移除背景图片引用
                updateCustomTheme(
                    themeId = themeId,
                    backgroundImage = null
                )
                
                return true
            }
        }
        
        return false
    }
    
    /**
     * 生成按键背景Drawable
     */
    fun createKeyBackgroundDrawable(theme: ThemeConfig = currentTheme): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = theme.keyRadius * Resources.getSystem().displayMetrics.density
            setColor(theme.keyColor)
            
            if (theme.keyBorderColor != Color.TRANSPARENT) {
                setStroke(
                    (1 * Resources.getSystem().displayMetrics.density).toInt(),
                    theme.keyBorderColor
                )
            }
        }
    }
    
    /**
     * 生成按键按下状态Drawable
     */
    fun createKeyPressedDrawable(theme: ThemeConfig = currentTheme): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = theme.keyRadius * Resources.getSystem().displayMetrics.density
            setColor(theme.keyPressedColor)
            
            if (theme.keyBorderColor != Color.TRANSPARENT) {
                setStroke(
                    (1 * Resources.getSystem().displayMetrics.density).toInt(),
                    theme.keyBorderColor
                )
            }
        }
    }
    
    /**
     * 生成功能键背景Drawable
     */
    fun createFunctionKeyBackgroundDrawable(theme: ThemeConfig = currentTheme): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = theme.keyRadius * Resources.getSystem().displayMetrics.density
            setColor(theme.functionKeyColor)
            
            if (theme.keyBorderColor != Color.TRANSPARENT) {
                setStroke(
                    (1 * Resources.getSystem().displayMetrics.density).toInt(),
                    theme.keyBorderColor
                )
            }
        }
    }
    
    /**
     * 生成空格键背景Drawable
     */
    fun createSpaceKeyBackgroundDrawable(theme: ThemeConfig = currentTheme): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = theme.keyRadius * Resources.getSystem().displayMetrics.density
            setColor(theme.spaceKeyColor)
            
            if (theme.keyBorderColor != Color.TRANSPARENT) {
                setStroke(
                    (1 * Resources.getSystem().displayMetrics.density).toInt(),
                    theme.keyBorderColor
                )
            }
        }
    }
    
    /**
     * 获取主题统计信息
     */
    fun getThemeStatistics(): Map<String, Any> {
        return mapOf(
            "current_theme" to currentTheme.name,
            "current_theme_id" to currentTheme.id,
            "builtin_themes_count" to 5,
            "custom_themes_count" to customThemes.size,
            "total_themes" to (5 + customThemes.size),
            "has_background_image" to (currentTheme.backgroundImage != null),
            "background_opacity" to currentTheme.backgroundOpacity,
            "key_radius" to currentTheme.keyRadius,
            "key_text_size" to currentTheme.keyTextSize,
            "last_used" to lastUsedTime
        )
    }
    
    /**
     * 重置为默认主题
     */
    fun resetToDefault(): Boolean {
        return setCurrentTheme(THEME_LIGHT)
    }
    
    /**
     * 加载当前主题
     */
    private fun loadCurrentTheme() {
        val themeId = prefs.getString(KEY_CURRENT_THEME, THEME_LIGHT) ?: THEME_LIGHT
        
        when (themeId) {
            THEME_LIGHT -> currentTheme = createLightTheme()
            THEME_DARK -> currentTheme = createDarkTheme()
            THEME_EYE_PROTECTION -> currentTheme = createEyeProtectionTheme()
            THEME_CYBERPUNK -> currentTheme = createCyberpunkTheme()
            THEME_INK_WASH -> currentTheme = createInkWashTheme()
            else -> {
                // 尝试加载自定义主题
                val customThemeJson = prefs.getString("custom_theme_$themeId", null)
                customThemeJson?.let {
                    try {
                        val json = JSONObject(it)
                        currentTheme = ThemeConfig(
                            id = json.getString("id"),
                            name = json.getString("name"),
                            backgroundColor = json.getInt("backgroundColor"),
                            keyColor = json.getInt("keyColor"),
                            keyPressedColor = json.getInt("keyPressedColor"),
                            keyTextColor = json.getInt("keyTextColor"),
                            keyBorderColor = json.getInt("keyBorderColor"),
                            functionKeyColor = json.getInt("functionKeyColor"),
                            spaceKeyColor = json.getInt("spaceKeyColor"),
                            keyRadius = json.getDouble("keyRadius").toFloat(),
                            keyTextSize = json.getDouble("keyTextSize").toFloat(),
                            backgroundImage = if (json.getString("backgroundImage").isNotEmpty()) 
                                json.getString("backgroundImage") else null,
                            backgroundOpacity = json.getDouble("backgroundOpacity").toFloat(),
                            isCustom = json.getBoolean("isCustom"),
                            createdTime = json.getLong("createdTime")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading custom theme: $themeId", e)
                        currentTheme = createLightTheme()
                    }
                } ?: run {
                    currentTheme = createLightTheme()
                }
            }
        }
    }
    
    /**
     * 保存当前主题
     */
    private fun saveCurrentTheme() {
        prefs.edit {
            putString(KEY_CURRENT_THEME, currentTheme.id)
            
            // 如果是自定义主题，保存完整配置
            if (currentTheme.isCustom) {
                val json = JSONObject().apply {
                    put("id", currentTheme.id)
                    put("name", currentTheme.name)
                    put("backgroundColor", currentTheme.backgroundColor)
                    put("keyColor", currentTheme.keyColor)
                    put("keyPressedColor", currentTheme.keyPressedColor)
                    put("keyTextColor", currentTheme.keyTextColor)
                    put("keyBorderColor", currentTheme.keyBorderColor)
                    put("functionKeyColor", currentTheme.functionKeyColor)
                    put("spaceKeyColor", currentTheme.spaceKeyColor)
                    put("keyRadius", currentTheme.keyRadius)
                    put("keyTextSize", currentTheme.keyTextSize)
                    put("backgroundImage", currentTheme.backgroundImage ?: "")
                    put("backgroundOpacity", currentTheme.backgroundOpacity)
                    put("isCustom", currentTheme.isCustom)
                    put("createdTime", currentTheme.createdTime)
                }
                putString("custom_theme_${currentTheme.id}", json.toString())
            }
        }
    }
    
    /**
     * 加载自定义主题
     */
    private fun loadCustomThemes() {
        val customThemesJson = prefs.getString(KEY_CUSTOM_THEMES, "[]")
        
        try {
            val jsonArray = org.json.JSONArray(customThemesJson)
            customThemes.clear()
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val theme = ThemeConfig(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    backgroundColor = json.getInt("backgroundColor"),
                    keyColor = json.getInt("keyColor"),
                    keyPressedColor = json.getInt("keyPressedColor"),
                    keyTextColor = json.getInt("keyTextColor"),
                    keyBorderColor = json.getInt("keyBorderColor"),
                    functionKeyColor = json.getInt("functionKeyColor"),
                    spaceKeyColor = json.getInt("spaceKeyColor"),
                    keyRadius = json.getDouble("keyRadius").toFloat(),
                    keyTextSize = json.getDouble("keyTextSize").toFloat(),
                    backgroundImage = if (json.getString("backgroundImage").isNotEmpty()) 
                        json.getString("backgroundImage") else null,
                    backgroundOpacity = json.getDouble("backgroundOpacity").toFloat(),
                    isCustom = json.getBoolean("isCustom"),
                    createdTime = json.getLong("createdTime")
                )
                customThemes.add(theme)
            }
            
            Log.d(TAG, "Loaded ${customThemes.size} custom themes")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom themes", e)
            customThemes.clear()
        }
    }
    
    /**
     * 保存自定义主题
     */
    private fun saveCustomThemes() {
        val jsonArray = org.json.JSONArray()
        
        customThemes.forEach { theme ->
            val json = JSONObject().apply {
                put("id", theme.id)
                put("name", theme.name)
                put("backgroundColor", theme.backgroundColor)
                put("keyColor", theme.keyColor)
                put("keyPressedColor", theme.keyPressedColor)
                put("keyTextColor", theme.keyTextColor)
                put("keyBorderColor", theme.keyBorderColor)
                put("functionKeyColor", theme.functionKeyColor)
                put("spaceKeyColor", theme.spaceKeyColor)
                put("keyRadius", theme.keyRadius)
                put("keyTextSize", theme.keyTextSize)
                put("backgroundImage", theme.backgroundImage ?: "")
                put("backgroundOpacity", theme.backgroundOpacity)
                put("isCustom", theme.isCustom)
                put("createdTime", theme.createdTime)
            }
            jsonArray.put(json)
        }
        
        prefs.edit {
            putString(KEY_CUSTOM_THEMES, jsonArray.toString())
        }
    }
    
    /**
     * 生成主题ID
     */
    private fun generateThemeId(name: String): String {
        val timestamp = System.currentTimeMillis()
        val normalizedName = name.lowercase().replace("[^a-z0-9]".toRegex(), "_")
        return "custom_${normalizedName}_$timestamp"
    }
    
    /**
     * 创建浅色主题
     */
    private fun createLightTheme(): ThemeConfig {
        return ThemeConfig(
            id = THEME_LIGHT,
            name = "浅色主题",
            backgroundColor = Color.parseColor("#FFFFFF"),
            keyColor = Color.parseColor("#F5F5F5"),
            keyPressedColor = Color.parseColor("#E0E0E0"),
            keyTextColor = Color.parseColor("#212121"),
            keyBorderColor = Color.parseColor("#BDBDBD"),
            functionKeyColor = Color.parseColor("#E3F2FD"),
            spaceKeyColor = Color.parseColor("#2196F3"),
            keyRadius = 8f,
            keyTextSize = 16f
        )
    }
    
    /**
     * 创建深色主题
     */
    private fun createDarkTheme(): ThemeConfig {
        return ThemeConfig(
            id = THEME_DARK,
            name = "深色主题",
            backgroundColor = Color.parseColor("#121212"),
            keyColor = Color.parseColor("#1E1E1E"),
            keyPressedColor = Color.parseColor("#2D2D2D"),
            keyTextColor = Color.parseColor("#FFFFFF"),
            keyBorderColor = Color.parseColor("#424242"),
            functionKeyColor = Color.parseColor("#37474F"),
            spaceKeyColor = Color.parseColor("#2196F3"),
            keyRadius = 8f,
            keyTextSize = 16f
        )
    }
    
    /**
     * 创建护眼主题
     */
    private fun createEyeProtectionTheme(): ThemeConfig {
        return ThemeConfig(
            id = THEME_EYE_PROTECTION,
            name = "护眼主题",
            backgroundColor = Color.parseColor("#F0F8E6"),
            keyColor = Color.parseColor("#E8F5E9"),
            keyPressedColor = Color.parseColor("#C8E6C9"),
            keyTextColor = Color.parseColor("#2E7D32"),
            keyBorderColor = Color.parseColor("#81C784"),
            functionKeyColor = Color.parseColor("#C8E6C9"),
            spaceKeyColor = Color.parseColor("#4CAF50"),
            keyRadius = 8f,
            keyTextSize = 16f
        )
    }
    
    /**
     * 创建赛博主题
     */
    private fun createCyberpunkTheme(): ThemeConfig {
        return ThemeConfig(
            id = THEME_CYBERPUNK,
            name = "赛博主题",
            backgroundColor = Color.parseColor("#0A0A0A"),
            keyColor = Color.parseColor("#00FF9D"),
            keyPressedColor = Color.parseColor("#00CC7D"),
            keyTextColor = Color.parseColor("#000000"),
            keyBorderColor = Color.parseColor("#00FF9D"),
            functionKeyColor = Color.parseColor("#9D00FF"),
            spaceKeyColor = Color.parseColor("#FF009D"),
            keyRadius = 4f,
            keyTextSize = 16f
        )
    }
    
    /**
     * 创建水墨主题
     */
    private fun createInkWashTheme(): ThemeConfig {
        return ThemeConfig(
            id = THEME_INK_WASH,
            name = "水墨主题",
            backgroundColor = Color.parseColor("#FAFAFA"),
            keyColor = Color.parseColor("#E8EAF6"),
            keyPressedColor = Color.parseColor("#C5CAE9"),
            keyTextColor = Color.parseColor("#3F51B5"),
            keyBorderColor = Color.parseColor("#7986CB"),
            functionKeyColor = Color.parseColor("#E8EAF6"),
            spaceKeyColor = Color.parseColor("#3F51B5"),
            keyRadius = 12f,
            keyTextSize = 16f
        )
    }
}