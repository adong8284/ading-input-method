# 阿栋输入法 - Ading Input Method

[![Build APK](https://github.com/adong8284/ading-input-method/actions/workflows/build.yml/badge.svg)](https://github.com/adong8284/ading-input-method/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/Android-21%2B-brightgreen.svg)](https://developer.android.com/about/versions/android-5.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8-blue.svg)](https://kotlinlang.org)

功能完整的Android输入法应用，支持八大核心功能模块。

## ✨ 功能特性

### 🎯 八大核心功能
1. **📝 拼音/五笔/英文输入** - 多输入法切换
2. **😊 全量Emoji键盘** - Unicode 15.1，搜索+最近使用
3. **🎨 智能主题系统** - 5套内置主题+自定义
4. **🎤 系统级语音输入** - 实时波形，多语言支持
5. **✍️ 手写输入** - ML Kit识别，多语言支持
6. **📋 剪贴板管理** - 加密存储，隐私保护
7. **↪️ 拼音滑行输入** - 自研算法，Trie树匹配
8. **📚 超大词库系统** - 50万+词汇，用户学习

### 🚀 技术特色
- **模块化架构** - 每个功能独立，易于维护扩展
- **现代化技术栈** - Kotlin + AndroidX + Material Design 3
- **完整设备适配** - API 21-36，平板横屏支持
- **性能优化** - 低内存设备词库分片加载
- **隐私保护** - Android 10+剪贴板权限处理

## 📱 快速开始

### 下载安装
1. 前往 [Releases](https://github.com/adong8284/ading-input-method/releases) 页面
2. 下载最新的 `app-debug.apk` 或 `app-release.apk`
3. 在手机上安装APK文件
4. 启用输入法：设置 → 语言和输入法 → 虚拟键盘 → 启用"阿栋输入法"

### 从源码构建
```bash
# 克隆仓库
git clone https://github.com/adong8284/ading-input-method.git
cd ading-input-method

# 构建调试版
./gradlew assembleDebug

# 构建发布版
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 🏗️ 项目结构
```
ading-input-method/
├── app/
│   ├── src/main/java/com/ading/inputmethod/
│   │   ├── AdingInputMethodService.kt    # 输入法主服务
│   │   ├── SettingsActivity.kt           # 设置页面
│   │   └── modules/                      # 功能模块
│   │       ├── KeyboardModule.kt         # 键盘模块基类
│   │       ├── PinyinModule.kt           # 拼音输入
│   │       ├── WubiModule.kt             # 五笔输入
│   │       ├── EnglishModule.kt          # 英文输入
│   │       ├── EmojiModule.kt            # 表情键盘
│   │       ├── VoiceModule.kt            # 语音输入
│   │       ├── HandwritingModule.kt      # 手写输入
│   │       ├── ClipboardModule.kt        # 剪贴板管理
│   │       ├── ThemeModule.kt            # 主题系统
│   │       ├── SwipeTypingModule.kt      # 滑行输入
│   │       └── DictionaryModule.kt       # 词库系统
│   ├── src/main/res/                     # 资源文件
│   └── build.gradle                      # 应用配置
├── .github/workflows/build.yml           # CI/CD配置
├── build.gradle                          # 项目配置
├── settings.gradle                       # 项目设置
├── gradlew                               # Gradle包装器
└── README.md                             # 项目说明
```

## 🔧 开发指南

### 环境要求
- Android Studio Flamingo 或更高版本
- JDK 17+
- Android SDK 33+
- Gradle 7.5+

### 导入项目
1. 使用Android Studio打开项目
2. 等待Gradle同步完成
3. 连接设备或启动模拟器
4. 点击运行按钮 ▶️

### 模块说明
每个功能模块都继承自 `KeyboardModule` 基类，实现统一的接口：
- `onCreate()` - 模块初始化
- `onDestroy()` - 资源清理
- `getView()` - 获取键盘视图
- `handleKey()` - 按键处理
- `switchTo()` - 切换到该模块

## 📖 使用说明

### 输入法切换
1. 在任何输入框长按
2. 选择"输入法"
3. 选择"阿栋输入法"

### 功能切换
- **拼音/五笔/英文**：点击键盘左下角切换按钮
- **表情键盘**：点击笑脸按钮
- **语音输入**：点击麦克风按钮
- **手写输入**：点击手写图标
- **主题切换**：点击主题按钮
- **剪贴板**：点击剪贴板图标

### 设置页面
点击键盘上的设置按钮进入设置页面，可以配置：
- 输入法偏好
- 主题选择
- 词库管理
- 隐私设置

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 开发流程
1. Fork本仓库
2. 创建功能分支 (`git checkout -b feature/awesome-feature`)
3. 提交更改 (`git commit -m 'Add awesome feature'`)
4. 推送到分支 (`git push origin feature/awesome-feature`)
5. 创建Pull Request

### 代码规范
- 使用Kotlin编写
- 遵循Android官方编码规范
- 添加中文注释
- 编写单元测试

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

感谢所有贡献者和用户的支持！

## 📞 联系方式

- 项目主页：https://github.com/adong8284/ading-input-method
- Issues：https://github.com/adong8284/ading-input-method/issues
- 讨论区：https://github.com/adong8284/ading-input-method/discussions

---

**阿栋输入法** - 让输入更智能，让沟通更顺畅！ 🚀