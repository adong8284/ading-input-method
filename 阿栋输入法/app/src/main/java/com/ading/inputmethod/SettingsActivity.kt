package com.ading.inputmethod

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.ading.inputmethod.modules.*

/**
 * 设置Activity
 * 提供输入法各项设置选项
 */
class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // 设置ActionBar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings_title)
        }
        
        // 加载设置Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    /**
     * 设置Fragment
     */
    class SettingsFragment : PreferenceFragmentCompat() {
        
        private lateinit var keyboardManager: KeyboardManager
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            // 初始化键盘管理器
            keyboardManager = KeyboardManager(requireContext())
            
            // 设置监听器
            setupPreferences()
        }
        
        private fun setupPreferences() {
            // 主题设置
            val themePreference = findPreference<ListPreference>("theme")
            themePreference?.setOnPreferenceChangeListener { _, newValue ->
                val themeId = newValue as String
                updateTheme(themeId)
                true
            }
            
            // 振动反馈
            val vibrationPreference = findPreference<SwitchPreferenceCompat>("vibration")
            vibrationPreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                keyboardManager.setVibrationEnabled(enabled)
                true
            }
            
            // 声音反馈
            val soundPreference = findPreference<SwitchPreferenceCompat>("sound")
            soundPreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                keyboardManager.setSoundEnabled(enabled)
                true
            }
            
            // 滑行输入
            val swipePreference = findPreference<SwitchPreferenceCompat>("swipe_typing")
            swipePreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                keyboardManager.setSwipeTypingEnabled(enabled)
                true
            }
            
            // 剪贴板历史
            val clipboardPreference = findPreference<SwitchPreferenceCompat>("clipboard_history")
            clipboardPreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                keyboardManager.setClipboardHistoryEnabled(enabled)
                true
            }
            
            // 用户词频学习
            val learningPreference = findPreference<SwitchPreferenceCompat>("user_learning")
            learningPreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                keyboardManager.getModule<DictionaryModule>()?.setUserLearningEnabled(enabled)
                true
            }
            
            // 云同步
            val syncPreference = findPreference<SwitchPreferenceCompat>("cloud_sync")
            syncPreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                keyboardManager.getModule<DictionaryModule>()?.setCloudSyncEnabled(enabled)
                true
            }
            
            // 主题配置
            val themeConfigPreference = findPreference<Preference>("theme_config")
            themeConfigPreference?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), ThemeConfigActivity::class.java)
                startActivity(intent)
                true
            }
            
            // 词库管理
            val dictionaryPreference = findPreference<Preference>("dictionary_manager")
            dictionaryPreference?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), DictionaryManagerActivity::class.java)
                startActivity(intent)
                true
            }
            
            // 剪贴板设置
            val clipboardSettingsPreference = findPreference<Preference>("clipboard_settings")
            clipboardSettingsPreference?.setOnPreferenceClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 需要用户手动开启剪贴板权限
                    val intent = Intent(Settings.ACTION_CLIPBOARD_SETTINGS)
                    startActivity(intent)
                } else {
                    // 旧版本直接打开剪贴板历史
                    val intent = Intent(requireContext(), ClipboardHistoryActivity::class.java)
                    startActivity(intent)
                }
                true
            }
            
            // 语音设置
            val voiceSettingsPreference = findPreference<Preference>("voice_settings")
            voiceSettingsPreference?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), VoiceInputActivity::class.java)
                startActivity(intent)
                true
            }
            
            // 手写设置
            val handwritingSettingsPreference = findPreference<Preference>("handwriting_settings")
            handwritingSettingsPreference?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), HandwritingActivity::class.java)
                startActivity(intent)
                true
            }
            
            // 关于
            val aboutPreference = findPreference<Preference>("about")
            aboutPreference?.setOnPreferenceClickListener {
                showAboutDialog()
                true
            }
            
            // 重置设置
            val resetPreference = findPreference<Preference>("reset_settings")
            resetPreference?.setOnPreferenceClickListener {
                showResetConfirmationDialog()
                true
            }
        }
        
        /**
         * 更新主题
         */
        private fun updateTheme(themeId: String) {
            val inputMethodService = activity as? AdingInputMethodService
            inputMethodService?.let { service ->
                when (themeId) {
                    "light" -> service.applyTheme(AdingInputMethodService.KeyboardTheme.LIGHT)
                    "dark" -> service.applyTheme(AdingInputMethodService.KeyboardTheme.DARK)
                    "eye_protection" -> service.applyTheme(AdingInputMethodService.KeyboardTheme.EYE_PROTECTION)
                    "cyberpunk" -> service.applyTheme(AdingInputMethodService.KeyboardTheme.CYBERPUNK)
                    "ink_wash" -> service.applyTheme(AdingInputMethodService.KeyboardTheme.INK_WASH)
                }
            }
        }
        
        /**
         * 显示关于对话框
         */
        private fun showAboutDialog() {
            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            
            val message = """
                阿栋输入法 v$versionName ($versionCode)
                
                功能特点：
                • 全键盘/九宫格/手写/语音/表情多种输入模式
                • 智能拼音/五笔/英文词库
                • 滑行输入支持
                • 剪贴板历史管理
                • 多种主题可选
                • 表情符号支持
                • 语音输入识别
                • 手写输入识别
                
                开发者：阿栋
                联系：ading@example.com
                
                © 2024 阿栋输入法 版权所有
            """.trimIndent()
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("关于阿栋输入法")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
        
        /**
         * 显示重置确认对话框
         */
        private fun showResetConfirmationDialog() {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("重置设置")
                .setMessage("确定要重置所有设置吗？此操作不可撤销。")
                .setPositiveButton("重置") { _, _ ->
                    resetAllSettings()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        /**
         * 重置所有设置
         */
        private fun resetAllSettings() {
            // 重置主题
            updateTheme("light")
            
            // 重置键盘管理器设置
            keyboardManager.setVibrationEnabled(true)
            keyboardManager.setSoundEnabled(true)
            keyboardManager.setSwipeTypingEnabled(false)
            keyboardManager.setClipboardHistoryEnabled(true)
            
            // 重置词库模块设置
            keyboardManager.getModule<DictionaryModule>()?.setUserLearningEnabled(true)
            keyboardManager.getModule<DictionaryModule>()?.setCloudSyncEnabled(false)
            
            // 重置滑行输入设置
            keyboardManager.getModule<SwipeTypingModule>()?.apply {
                setSwipeEnabled(true)
                setVibrationEnabled(true)
                setTrailEnabled(true)
                setSensitivity(0.5f)
                setLearningEnabled(true)
                saveSettings()
            }
            
            // 显示重置完成提示
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("重置完成")
                .setMessage("所有设置已重置为默认值。")
                .setPositiveButton("确定", null)
                .show()
        }
    }
}