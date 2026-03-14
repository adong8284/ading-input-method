package com.ading.inputmethod.modules

import android.content.Context
import android.os.Vibrator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants

/**
 * 键盘管理器
 * 负责管理所有键盘模块的注册、初始化和调度
 */
class KeyboardManager(private val context: Context) {
    
    // 模块映射表
    private val modules = mutableMapOf<Class<out KeyboardModule>, KeyboardModule>()
    
    // 振动器
    private val vibrator: Vibrator? by lazy {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    
    // 音频管理器
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    // 配置
    private var vibrationEnabled = true
    private var soundEnabled = true
    private var swipeTypingEnabled = false
    private var clipboardHistoryEnabled = true
    
    /**
     * 注册模块
     */
    fun registerModule(module: KeyboardModule) {
        modules[module.javaClass] = module
        module.initialize()
    }
    
    /**
     * 获取指定类型的模块
     */
    inline fun <reified T : KeyboardModule> getModule(): T? {
        return modules[T::class.java] as? T
    }
    
    /**
     * 初始化所有模块
     */
    fun initializeAllModules() {
        modules.values.forEach { it.initialize() }
    }
    
    /**
     * 释放所有模块资源
     */
    fun releaseAllModules() {
        modules.values.forEach { it.release() }
        modules.clear()
    }
    
    /**
     * 处理按键事件
     */
    fun handleKeyEvent(keyCode: Int, isLongPress: Boolean = false): Boolean {
        // 首先检查是否有模块可以处理
        modules.values.forEach { module ->
            if (module.canHandleKeyEvent(keyCode, isLongPress)) {
                return module.handleKeyEvent(keyCode, isLongPress)
            }
        }
        
        // 默认处理：提供触觉反馈
        provideHapticFeedback()
        
        // 提供声音反馈
        if (soundEnabled) {
            provideSoundFeedback()
        }
        
        return false
    }
    
    /**
     * 处理文本输入
     */
    fun handleTextInput(text: String) {
        // 通知所有文本处理模块
        modules.values.forEach { module ->
            if (module is TextProcessingModule) {
                module.processTextInput(text)
            }
        }
    }
    
    /**
     * 处理滑动手势
     */
    fun handleSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float): String? {
        if (!swipeTypingEnabled) return null
        
        // 查找滑行输入模块
        val swipeModule = getModule<SwipeTypingModule>()
        return swipeModule?.recognizeSwipe(startX, startY, endX, endY)
    }
    
    /**
     * 设置振动反馈是否启用
     */
    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
    }
    
    /**
     * 设置声音反馈是否启用
     */
    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }
    
    /**
     * 设置滑行输入是否启用
     */
    fun setSwipeTypingEnabled(enabled: Boolean) {
        swipeTypingEnabled = enabled
    }
    
    /**
     * 设置剪贴板历史是否启用
     */
    fun setClipboardHistoryEnabled(enabled: Boolean) {
        clipboardHistoryEnabled = enabled
        getModule<ClipboardModule>()?.setEnabled(enabled)
    }
    
    /**
     * 提供触觉反馈
     */
    fun provideHapticFeedback() {
        if (!vibrationEnabled || vibrator == null) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用VibrationEffect
                val effect = VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator?.vibrate(effect)
            } else {
                // 旧版本使用传统振动
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (e: Exception) {
            // 忽略振动异常
        }
    }
    
    /**
     * 提供声音反馈
     */
    fun provideSoundFeedback() {
        try {
            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
        } catch (e: Exception) {
            // 忽略声音异常
        }
    }
    
    /**
     * 获取所有模块状态
     */
    fun getModuleStatus(): Map<String, Boolean> {
        return modules.mapValues { (_, module) -> module.isEnabled() }
    }
    
    /**
     * 启用/禁用特定模块
     */
    fun setModuleEnabled(moduleClass: Class<out KeyboardModule>, enabled: Boolean) {
        modules[moduleClass]?.setEnabled(enabled)
    }
    
    /**
     * 保存所有模块状态
     */
    fun saveAllStates() {
        modules.values.forEach { it.saveState() }
    }
    
    /**
     * 恢复所有模块状态
     */
    fun restoreAllStates() {
        modules.values.forEach { it.restoreState() }
    }
    
    /**
     * 处理配置变更
     */
    fun onConfigurationChanged() {
        modules.values.forEach { it.onConfigurationChanged() }
    }
    
    /**
     * 处理低内存警告
     */
    fun onLowMemory() {
        modules.values.forEach { it.onLowMemory() }
    }
    
    /**
     * 获取模块统计信息
     */
    fun getModuleStatistics(): Map<String, Any> {
        return modules.mapValues { (_, module) ->
            mapOf(
                "enabled" to module.isEnabled(),
                "initialized" to module.isInitialized(),
                "lastUsed" to module.getLastUsedTime()
            )
        }
    }
}