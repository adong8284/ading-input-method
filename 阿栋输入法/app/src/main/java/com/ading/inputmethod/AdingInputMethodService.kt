package com.ading.inputmethod

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.isVisible
import com.ading.inputmethod.databinding.KeyboardMainBinding
import com.ading.inputmethod.modules.*

/**
 * 阿栋输入法主服务类
 * 继承自InputMethodService，负责管理键盘输入和模块调度
 */
class AdingInputMethodService : InputMethodService() {
    
    // 视图绑定
    private lateinit var binding: KeyboardMainBinding
    
    // 键盘模块管理器
    private lateinit var keyboardManager: KeyboardManager
    
    // 当前输入模式
    private var currentMode: InputMode = InputMode.QWERTY
    
    // 当前主题
    private var currentTheme: KeyboardTheme = KeyboardTheme.LIGHT
    
    // 输入模式枚举
    enum class InputMode {
        QWERTY,      // 全键盘
        NINE_KEY,    // 九宫格
        HANDWRITING, // 手写
        VOICE,       // 语音
        EMOJI        // 表情
    }
    
    // 主题枚举
    enum class KeyboardTheme {
        LIGHT,          // 浅色主题
        DARK,           // 深色主题
        EYE_PROTECTION, // 护眼主题
        CYBERPUNK,      // 赛博主题
        INK_WASH        // 水墨主题
    }
    
    override fun onCreate() {
        super.onCreate()
        // 初始化键盘管理器
        keyboardManager = KeyboardManager(this)
        
        // 加载用户设置
        loadUserSettings()
        
        // 初始化各模块
        initializeModules()
    }
    
    override fun onCreateInputView(): View {
        // 加载键盘布局
        binding = KeyboardMainBinding.inflate(LayoutInflater.from(this))
        
        // 设置键盘主题
        applyTheme(currentTheme)
        
        // 初始化键盘视图
        initializeKeyboardView()
        
        // 设置按键监听器
        setupKeyListeners()
        
        return binding.root
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        
        // 根据输入类型调整键盘
        info?.let { adjustKeyboardForInputType(it) }
        
        // 显示键盘
        showKeyboard()
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        
        // 清理资源
        cleanupResources()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 释放所有模块资源
        keyboardManager.releaseAllModules()
    }
    
    /**
     * 初始化所有功能模块
     */
    private fun initializeModules() {
        // 拼音模块
        keyboardManager.registerModule(PinyinModule(this))
        
        // 五笔模块
        keyboardManager.registerModule(WubiModule(this))
        
        // 英文模块
        keyboardManager.registerModule(EnglishModule(this))
        
        // 表情模块
        keyboardManager.registerModule(EmojiModule(this))
        
        // 语音模块
        keyboardManager.registerModule(VoiceModule(this))
        
        // 手写模块
        keyboardManager.registerModule(HandwritingModule(this))
        
        // 剪贴板模块
        keyboardManager.registerModule(ClipboardModule(this))
        
        // 主题模块
        keyboardManager.registerModule(ThemeModule(this))
        
        // 词库模块
        keyboardManager.registerModule(DictionaryModule(this))
    }
    
    /**
     * 初始化键盘视图
     */
    private fun initializeKeyboardView() {
        // 设置键盘布局
        when (currentMode) {
            InputMode.QWERTY -> showQwertyKeyboard()
            InputMode.NINE_KEY -> showNineKeyKeyboard()
            InputMode.HANDWRITING -> showHandwritingKeyboard()
            InputMode.VOICE -> showVoiceKeyboard()
            InputMode.EMOJI -> showEmojiKeyboard()
        }
        
        // 设置功能按钮
        setupFunctionButtons()
    }
    
    /**
     * 显示全键盘布局
     */
    private fun showQwertyKeyboard() {
        binding.keyboardContainer.removeAllViews()
        
        // 加载全键盘布局
        val qwertyView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_qwerty, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(qwertyView)
        
        // 更新模式指示器
        binding.modeIndicator.text = getString(R.string.keyboard_type_qwerty)
    }
    
    /**
     * 显示九宫格键盘布局
     */
    private fun showNineKeyKeyboard() {
        binding.keyboardContainer.removeAllViews()
        
        // 加载九宫格布局
        val nineKeyView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_nine_key, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(nineKeyView)
        
        // 更新模式指示器
        binding.modeIndicator.text = getString(R.string.keyboard_type_nine)
    }
    
    /**
     * 显示手写键盘布局
     */
    private fun showHandwritingKeyboard() {
        binding.keyboardContainer.removeAllViews()
        
        // 加载手写布局
        val handwritingView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_handwriting, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(handwritingView)
        
        // 初始化手写板
        initializeHandwritingArea(handwritingView)
        
        // 更新模式指示器
        binding.modeIndicator.text = getString(R.string.keyboard_type_handwriting)
    }
    
    /**
     * 显示语音键盘布局
     */
    private fun showVoiceKeyboard() {
        binding.keyboardContainer.removeAllViews()
        
        // 加载语音布局
        val voiceView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_voice, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(voiceView)
        
        // 初始化语音识别
        initializeVoiceRecognition(voiceView)
        
        // 更新模式指示器
        binding.modeIndicator.text = getString(R.string.keyboard_type_voice)
    }
    
    /**
     * 显示表情键盘布局
     */
    private fun showEmojiKeyboard() {
        binding.keyboardContainer.removeAllViews()
        
        // 加载表情布局
        val emojiView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_emoji, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(emojiView)
        
        // 初始化表情网格
        initializeEmojiGrid(emojiView)
        
        // 更新模式指示器
        binding.modeIndicator.text = getString(R.string.keyboard_type_emoji)
    }
    
    /**
     * 设置功能按钮
     */
    private fun setupFunctionButtons() {
        // 模式切换按钮
        binding.btnModeSwitch.setOnClickListener {
            cycleInputMode()
        }
        
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            openSettings()
        }
        
        // 主题切换按钮
        binding.btnTheme.setOnClickListener {
            cycleTheme()
        }
        
        // 剪贴板按钮
        binding.btnClipboard.setOnClickListener {
            showClipboardHistory()
        }
        
        // 表情按钮
        binding.btnEmoji.setOnClickListener {
            switchToMode(InputMode.EMOJI)
        }
        
        // 语音按钮
        binding.btnVoice.setOnClickListener {
            switchToMode(InputMode.VOICE)
        }
        
        // 手写按钮
        binding.btnHandwriting.setOnClickListener {
            switchToMode(InputMode.HANDWRITING)
        }
    }
    
    /**
     * 设置按键监听器
     */
    private fun setupKeyListeners() {
        // 数字键监听
        setupNumberKeys()
        
        // 字母键监听
        setupLetterKeys()
        
        // 功能键监听
        setupFunctionKeys()
        
        // 空格键监听
        setupSpaceKey()
        
        // 删除键监听
        setupDeleteKey()
        
        // 回车键监听
        setupEnterKey()
    }
    
    /**
     * 设置数字键监听
     */
    private fun setupNumberKeys() {
        // 0-9数字键
        for (i in 0..9) {
            val keyId = resources.getIdentifier("key_$i", "id", packageName)
            val keyView = binding.root.findViewById<View>(keyId)
            keyView?.setOnClickListener {
                val number = i.toString()
                currentInputConnection?.commitText(number, 1)
            }
        }
    }
    
    /**
     * 设置字母键监听
     */
    private fun setupLetterKeys() {
        // A-Z字母键
        for (c in 'A'..'Z') {
            val keyId = resources.getIdentifier("key_${c.lowercaseChar()}", "id", packageName)
            val keyView = binding.root.findViewById<View>(keyId)
            keyView?.setOnClickListener {
                val letter = c.toString()
                currentInputConnection?.commitText(letter, 1)
            }
        }
    }
    
    /**
     * 设置功能键监听
     */
    private fun setupFunctionKeys() {
        // 逗号
        binding.keyComma?.setOnClickListener {
            currentInputConnection?.commitText(",", 1)
        }
        
        // 句号
        binding.keyPeriod?.setOnClickListener {
            currentInputConnection?.commitText(".", 1)
        }
        
        // 问号
        binding.keyQuestion?.setOnClickListener {
            currentInputConnection?.commitText("?", 1)
        }
        
        // 感叹号
        binding.keyExclamation?.setOnClickListener {
            currentInputConnection?.commitText("!", 1)
        }
    }
    
    /**
     * 设置空格键监听
     */
    private fun setupSpaceKey() {
        binding.keySpace?.setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }
        
        // 长按空格键切换输入法
        binding.keySpace?.setOnLongClickListener {
            switchToNextInputMethod()
            true
        }
    }
    
    /**
     * 设置删除键监听
     */
    private fun setupDeleteKey() {
        binding.keyDelete?.setOnClickListener {
            val ic = currentInputConnection
            ic?.deleteSurroundingText(1, 0)
        }
        
        // 长按删除键清空输入
        binding.keyDelete?.setOnLongClickListener {
            val ic = currentInputConnection
            ic?.let { connection ->
                // 获取当前文本
                val textBeforeCursor = connection.getTextBeforeCursor(100, 0)
                textBeforeCursor?.let { text ->
                    connection.deleteSurroundingText(text.length, 0)
                }
            }
            true
        }
    }
    
    /**
     * 设置回车键监听
     */
    private fun setupEnterKey() {
        binding.keyEnter?.setOnClickListener {
            val ic = currentInputConnection
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
    
    /**
     * 循环切换输入模式
     */
    private fun cycleInputMode() {
        currentMode = when (currentMode) {
            InputMode.QWERTY -> InputMode.NINE_KEY
            InputMode.NINE_KEY -> InputMode.HANDWRITING
            InputMode.HANDWRITING -> InputMode.VOICE
            InputMode.VOICE -> InputMode.EMOJI
            InputMode.EMOJI -> InputMode.QWERTY
        }
        
        // 更新键盘显示
        initializeKeyboardView()
    }
    
    /**
     * 切换到指定输入模式
     */
    private fun switchToMode(mode: InputMode) {
        currentMode = mode
        initializeKeyboardView()
    }
    
    /**
     * 循环切换主题
     */
    private fun cycleTheme() {
        currentTheme = when (currentTheme) {
            KeyboardTheme.LIGHT -> KeyboardTheme.DARK
            KeyboardTheme.DARK -> KeyboardTheme.EYE_PROTECTION
            KeyboardTheme.EYE_PROTECTION -> KeyboardTheme.CYBERPUNK
            KeyboardTheme.CYBERPUNK -> KeyboardTheme.INK_WASH
            KeyboardTheme.INK_WASH -> KeyboardTheme.LIGHT
        }
        
        // 应用新主题
        applyTheme(currentTheme)
        
        // 保存主题设置
        saveThemePreference()
    }
    
    /**
     * 应用主题
     */
    private fun applyTheme(theme: KeyboardTheme) {
        when (theme) {
            KeyboardTheme.LIGHT -> applyLightTheme()
            KeyboardTheme.DARK -> applyDarkTheme()
            KeyboardTheme.EYE_PROTECTION -> applyEyeProtectionTheme()
            KeyboardTheme.CYBERPUNK -> applyCyberpunkTheme()
            KeyboardTheme.INK_WASH -> applyInkWashTheme()
        }
    }
    
    /**
     * 应用浅色主题
     */
    private fun applyLightTheme() {
        binding.root.setBackgroundColor(resources.getColor(R.color.theme_light_background, null))
        // 更新所有按键颜色
        updateKeyColors(R.color.theme_light_key, R.color.theme_light_text)
    }
    
    /**
     * 应用深色主题
     */
    private fun applyDarkTheme() {
        binding.root.setBackgroundColor(resources.getColor(R.color.theme_dark_background, null))
        updateKeyColors(R.color.theme_dark_key, R.color.theme_dark_text)
    }
    
    /**
     * 应用护眼主题
     */
    private fun applyEyeProtectionTheme() {
        binding.root.setBackgroundColor(resources.getColor(R.color.theme_eye_protection_background, null))
        updateKeyColors(R.color.theme_eye_protection_key, R.color.theme_eye_protection_text)
    }
    
    /**
     * 应用赛博主题
     */
    private fun applyCyberpunkTheme() {
        binding.root.setBackgroundColor(resources.getColor(R.color.theme_cyberpunk_background, null))
        updateKeyColors(R.color.theme_cyberpunk_key, R.color.theme_cyberpunk_text)
    }
    
    /**
     * 应用水墨主题
     */
    private fun applyInkWashTheme() {
        binding.root.setBackgroundColor(resources.getColor(R.color.theme_ink_wash_background, null))
        updateKeyColors(R.color.theme_ink_wash_key, R.color.theme_ink_wash_text)
    }
    
    /**
     * 更新按键颜色
     */
    private fun updateKeyColors(keyColorRes: Int, textColorRes: Int) {
        // 这里需要遍历所有按键视图并更新颜色
        // 简化实现：在实际项目中需要具体实现
    }
    
    /**
     * 根据输入类型调整键盘
     */
    private fun adjustKeyboardForInputType(info: EditorInfo) {
        when (info.inputType and android.text.InputType.TYPE_MASK_CLASS) {
            android.text.InputType.TYPE_CLASS_TEXT -> {
                // 文本输入
                when (info.inputType and android.text.InputType.TYPE_MASK_VARIATION) {
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                        // 邮箱地址，显示@和.键
                        showEmailKeys()
                    }
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD -> {
                        // 密码输入，隐藏预测文本
                        hidePrediction()
                    }
                    android.text.InputType.TYPE_TEXT_VARIATION_URI -> {
                        // URL输入，显示/和.键
                        showUrlKeys()
                    }
                }
            }
            android.text.InputType.TYPE_CLASS_NUMBER -> {
                // 数字输入，切换到数字键盘
                showNumberKeyboard()
            }
            android.text.InputType.TYPE_CLASS_PHONE -> {
                // 电话输入，显示电话键盘
                showPhoneKeyboard()
            }
        }
    }
    
    /**
     * 显示邮箱相关按键
     */
    private fun showEmailKeys() {
        // 显示@和.键
        binding.keyAt?.isVisible = true
        binding.keyDot?.isVisible = true
    }
    
    /**
     * 显示URL相关按键
     */
    private fun showUrlKeys() {
        // 显示/、.、:等键
        binding.keySlash?.isVisible = true
        binding.keyColon?.isVisible = true
        binding.keyDot?.isVisible = true
    }
    
    /**
     * 显示数字键盘
     */
    private fun showNumberKeyboard() {
        // 切换到数字键盘布局
        binding.keyboardContainer.removeAllViews()
        val numberView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_number, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(numberView)
    }
    
    /**
     * 显示电话键盘
     */
    private fun showPhoneKeyboard() {
        // 切换到电话键盘布局
        binding.keyboardContainer.removeAllViews()
        val phoneView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_phone, binding.keyboardContainer, false)
        binding.keyboardContainer.addView(phoneView)
    }
    
    /**
     * 隐藏预测文本
     */
    private fun hidePrediction() {
        binding.predictionBar?.isVisible = false
    }
    
    /**
     * 打开设置界面
     */
    private fun openSettings() {
        // 启动设置Activity
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    /**
     * 显示剪贴板历史
     */
    private fun showClipboardHistory() {
        // 启动剪贴板历史Activity
        val intent = android.content.Intent(this, ClipboardHistoryActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    /**
     * 初始化手写区域
     */
    private fun initializeHandwritingArea(view: View) {
        // 获取手写视图
        val handwritingView = view.findViewById<android.gesture.GestureOverlayView>(R.id.handwriting_view)
        handwritingView?.let {
            // 设置手写监听器
            it.addOnGesturePerformedListener { _, gesture ->
                // 识别手势
                recognizeHandwriting(gesture)
            }
        }
    }
    
    /**
     * 初始化语音识别
     */
    private fun initializeVoiceRecognition(view: View) {
        // 获取语音按钮
        val voiceButton = view.findViewById<android.widget.Button>(R.id.btn_start_voice)
        voiceButton?.setOnClickListener {
            // 开始语音识别
            startVoiceRecognition()
        }
    }
    
    /**
     * 初始化表情网格
     */
    private fun initializeEmojiGrid(view: View) {
        // 获取表情网格视图
        val emojiGridView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.emoji_grid)
        emojiGridView?.let {
            // 设置表情适配器
            val emojiAdapter = EmojiAdapter(getEmojiList())
            it.adapter = emojiAdapter
            
            // 设置网格布局
            it.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 8)
            
            // 设置点击监听
            emojiAdapter.setOnItemClickListener { emoji ->
                currentInputConnection?.commitText(emoji, 1)
            }
        }
    }
    
    /**
     * 识别手写
     */
    private fun recognizeHandwriting(gesture: android.gesture.Gesture) {
        // 使用ML Kit进行手写识别
        keyboardManager.getModule<HandwritingModule>()?.recognize(gesture) { result ->
            result?.let {
                currentInputConnection?.commitText(it, 1)
            }
        }
    }
    
    /**
     * 开始语音识别
     */
    private fun startVoiceRecognition() {
        // 检查权限
        if (checkVoicePermission()) {
            keyboardManager.getModule<VoiceModule>()?.startRecognition { text ->
                text?.let {
                    currentInputConnection?.commitText(it, 1)
                }
            }
        } else {
            // 请求权限
            requestVoicePermission()
        }
    }
    
    /**
     * 检查语音权限
     */
    private fun checkVoicePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * 请求语音权限
     */
    private fun requestVoicePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 
                REQUEST_CODE_VOICE_PERMISSION)
        }
    }
    
    /**
     * 获取表情列表
     */
    private fun getEmojiList(): List<String> {
        // 这里应该从资源文件或数据库加载表情
        // 简化实现：返回一些常用表情
        return listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
            "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏"
        )
    }
    
    /**
     * 加载用户设置
     */
    private fun loadUserSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        
        // 加载主题设置
        val themeName = prefs.getString(PREF_THEME, KeyboardTheme.LIGHT.name)
        currentTheme = KeyboardTheme.valueOf(themeName ?: KeyboardTheme.LIGHT.name)
        
        // 加载输入模式
        val modeName = prefs.getString(PREF_INPUT_MODE, InputMode.QWERTY.name)
        currentMode = InputMode.valueOf(modeName ?: InputMode.QWERTY.name)
        
        // 加载其他设置
        val vibrationEnabled = prefs.getBoolean(PREF_VIBRATION, true)
        val soundEnabled = prefs.getBoolean(PREF_SOUND, true)
        val swipeTypingEnabled = prefs.getBoolean(PREF_SWIPE_TYPING, false)
        val clipboardHistoryEnabled = prefs.getBoolean(PREF_CLIPBOARD_HISTORY, true)
        
        // 应用设置
        applyUserSettings(vibrationEnabled, soundEnabled, swipeTypingEnabled, clipboardHistoryEnabled)
    }
    
    /**
     * 应用用户设置
     */
    private fun applyUserSettings(
        vibration: Boolean,
        sound: Boolean,
        swipeTyping: Boolean,
        clipboardHistory: Boolean
    ) {
        // 设置振动反馈
        keyboardManager.setVibrationEnabled(vibration)
        
        // 设置声音反馈
        keyboardManager.setSoundEnabled(sound)
        
        // 设置滑行输入
        keyboardManager.setSwipeTypingEnabled(swipeTyping)
        
        // 设置剪贴板历史
        keyboardManager.setClipboardHistoryEnabled(clipboardHistory)
    }
    
    /**
     * 保存主题偏好
     */
    private fun saveThemePreference() {
        val prefs = getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_THEME, currentTheme.name)
            .apply()
    }
    
    /**
     * 清理资源
     */
    private fun cleanupResources() {
        // 停止语音识别
        keyboardManager.getModule<VoiceModule>()?.stopRecognition()
        
        // 停止手写识别
        keyboardManager.getModule<HandwritingModule>()?.cleanup()
        
        // 保存当前状态
        saveCurrentState()
    }
    
    /**
     * 保存当前状态
     */
    private fun saveCurrentState() {
        val prefs = getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_INPUT_MODE, currentMode.name)
            .apply()
    }
    
    /**
     * 显示键盘
     */
    private fun showKeyboard() {
        // 触发键盘显示动画
        binding.root.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }
    
    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        // 触发键盘隐藏动画
        binding.root.animate()
            .alpha(0f)
            .setDuration(200)
            .start()
    }
    
    companion object {
        // SharedPreferences名称
        private const val PREFS_NAME = "ading_input_method_prefs"
        
        // 偏好键名
        private const val PREF_THEME = "theme"
        private const val PREF_INPUT_MODE = "input_mode"
        private const val PREF_VIBRATION = "vibration"
        private const val PREF_SOUND = "sound"
        private const val PREF_SWIPE_TYPING = "swipe_typing"
        private const val PREF_CLIPBOARD_HISTORY = "clipboard_history"
        
        // 请求码
        private const val REQUEST_CODE_VOICE_PERMISSION = 1001
    }
}