package com.ading.inputmethod

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import com.ading.inputmethod.databinding.KeyboardMainBinding

/**
 * 简化版输入法服务 - 用于AIDE快速测试
 */
class SimpleInputMethodService : InputMethodService() {
    
    private lateinit var binding: KeyboardMainBinding
    
    override fun onCreateInputView(): View {
        // 加载布局
        binding = KeyboardMainBinding.inflate(LayoutInflater.from(this))
        
        // 设置基本按键监听
        setupBasicKeys()
        
        return binding.root
    }
    
    private fun setupBasicKeys() {
        // 设置数字键
        binding.key0.setOnClickListener { sendKey('0') }
        binding.key1.setOnClickListener { sendKey('1') }
        binding.key2.setOnClickListener { sendKey('2') }
        binding.key3.setOnClickListener { sendKey('3') }
        binding.key4.setOnClickListener { sendKey('4') }
        binding.key5.setOnClickListener { sendKey('5') }
        binding.key6.setOnClickListener { sendKey('6') }
        binding.key7.setOnClickListener { sendKey('7') }
        binding.key8.setOnClickListener { sendKey('8') }
        binding.key9.setOnClickListener { sendKey('9') }
        
        // 设置字母键（仅示例）
        binding.keyQ.setOnClickListener { sendKey('q') }
        binding.keyW.setOnClickListener { sendKey('w') }
        binding.keyE.setOnClickListener { sendKey('e') }
        binding.keyR.setOnClickListener { sendKey('r') }
        binding.keyT.setOnClickListener { sendKey('t') }
        binding.keyY.setOnClickListener { sendKey('y') }
        binding.keyU.setOnClickListener { sendKey('u') }
        binding.keyI.setOnClickListener { sendKey('i') }
        binding.keyO.setOnClickListener { sendKey('o') }
        binding.keyP.setOnClickListener { sendKey('p') }
        
        // 功能键
        binding.keySpace.setOnClickListener { sendKey(' ') }
        binding.keyBackspace.setOnClickListener { deleteText() }
        binding.keyEnter.setOnClickListener { sendKey(KeyEvent.KEYCODE_ENTER) }
        
        // 模式切换
        binding.btnSwitchMode.setOnClickListener { switchInputMode() }
        
        // 设置按钮
        binding.btnSettings.setOnClickListener { openSettings() }
        
        // 主题按钮
        binding.btnTheme.setOnClickListener { switchTheme() }
        
        // 表情按钮
        binding.btnEmoji.setOnClickListener { showEmojiKeyboard() }
        
        // 语音按钮
        binding.btnVoice.setOnClickListener { startVoiceInput() }
        
        // 手写按钮
        binding.btnHandwriting.setOnClickListener { showHandwritingKeyboard() }
        
        // 剪贴板按钮
        binding.btnClipboard.setOnClickListener { showClipboard() }
    }
    
    private fun sendKey(character: Char) {
        val ic: InputConnection? = currentInputConnection
        ic?.commitText(character.toString(), 1)
    }
    
    private fun sendKey(keyCode: Int) {
        val ic: InputConnection? = currentInputConnection
        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
    
    private fun deleteText() {
        val ic: InputConnection? = currentInputConnection
        ic?.deleteSurroundingText(1, 0)
    }
    
    private fun switchInputMode() {
        // 简单的模式切换
        when (binding.keyboardContainer.tag as? String ?: "qwerty") {
            "qwerty" -> {
                binding.keyboardContainer.tag = "nine_key"
                // 这里可以切换到九宫格布局
            }
            "nine_key" -> {
                binding.keyboardContainer.tag = "qwerty"
                // 这里可以切换回全键盘布局
            }
        }
    }
    
    private fun openSettings() {
        // 打开设置页面
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    private fun switchTheme() {
        // 简单的主题切换
        val themes = listOf("light", "dark", "eye_protection")
        val currentTheme = binding.root.tag as? String ?: "light"
        val nextIndex = (themes.indexOf(currentTheme) + 1) % themes.size
        val nextTheme = themes[nextIndex]
        
        binding.root.tag = nextTheme
        applyTheme(nextTheme)
    }
    
    private fun applyTheme(theme: String) {
        // 应用主题颜色
        when (theme) {
            "light" -> {
                binding.root.setBackgroundColor(android.graphics.Color.WHITE)
                // 设置浅色主题
            }
            "dark" -> {
                binding.root.setBackgroundColor(android.graphics.Color.BLACK)
                // 设置深色主题
            }
            "eye_protection" -> {
                binding.root.setBackgroundColor(android.graphics.Color.parseColor("#C7EDCC"))
                // 设置护眼主题
            }
        }
    }
    
    private fun showEmojiKeyboard() {
        // 显示表情键盘
        // 这里可以加载表情布局
    }
    
    private fun startVoiceInput() {
        // 启动语音输入
        // 这里可以调用语音识别
    }
    
    private fun showHandwritingKeyboard() {
        // 显示手写键盘
        // 这里可以加载手写布局
    }
    
    private fun showClipboard() {
        // 显示剪贴板
        // 这里可以显示剪贴板历史
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // 输入开始时的处理
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        // 输入结束时的处理
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
    }
}