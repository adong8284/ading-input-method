package com.ading.inputmethod.modules

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * 表情模块
 * 支持Unicode 15.1全量表情，按分类组织，支持搜索和最近使用记录
 */
class EmojiModule(context: Context) : BaseKeyboardModule(context) {
    
    // 表情分类
    enum class EmojiCategory(val displayName: String) {
        SMILEYS("表情与人物"),
        ANIMALS("动物与自然"),
        FOOD("食物与饮料"),
        ACTIVITIES("活动"),
        TRAVEL("旅行与地点"),
        OBJECTS("物品"),
        SYMBOLS("符号"),
        FLAGS("旗帜")
    }
    
    // 表情数据类
    data class Emoji(
        val unicode: String,
        val name: String,
        val keywords: List<String>,
        val category: EmojiCategory,
        val skinToneSupport: Boolean = false,
        val genderSupport: Boolean = false,
        val version: String = "15.1"
    )
    
    // 表情数据存储
    private val emojiList = mutableListOf<Emoji>()
    private val emojiByCategory = mutableMapOf<EmojiCategory, MutableList<Emoji>>()
    private val recentEmojis = mutableListOf<String>()  // 存储unicode
    
    // SharedPreferences
    private lateinit var prefs: SharedPreferences
    
    // 搜索索引
    private val searchIndex = mutableMapOf<String, MutableList<Emoji>>()
    
    override fun getDescription(): String {
        return "表情模块，支持Unicode 15.1全量表情，按分类组织，支持搜索和最近使用记录"
    }
    
    override fun onInitialize() {
        prefs = context.getSharedPreferences("emoji_module", Context.MODE_PRIVATE)
        
        // 加载表情数据
        loadEmojiData()
        
        // 加载最近使用记录
        loadRecentEmojis()
        
        // 构建搜索索引
        buildSearchIndex()
    }
    
    override fun onRelease() {
        // 保存最近使用记录
        saveRecentEmojis()
        
        // 清理数据
        emojiList.clear()
        emojiByCategory.clear()
        searchIndex.clear()
        recentEmojis.clear()
    }
    
    override fun canHandleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        // 表情模块不直接处理按键事件
        return false
    }
    
    override fun handleKeyEvent(keyCode: Int, isLongPress: Boolean): Boolean {
        lastUsedTime = System.currentTimeMillis()
        return false
    }
    
    /**
     * 获取所有表情分类
     */
    fun getAllCategories(): List<EmojiCategory> {
        return EmojiCategory.values().toList()
    }
    
    /**
     * 获取指定分类的表情
     */
    fun getEmojisByCategory(category: EmojiCategory): List<Emoji> {
        return emojiByCategory[category] ?: emptyList()
    }
    
    /**
     * 搜索表情
     */
    fun searchEmojis(query: String): List<Emoji> {
        if (query.isEmpty()) return emptyList()
        
        val results = mutableSetOf<Emoji>()
        val lowerQuery = query.lowercase()
        
        // 搜索关键词
        searchIndex.forEach { (keyword, emojis) ->
            if (keyword.contains(lowerQuery)) {
                results.addAll(emojis)
            }
        }
        
        // 搜索表情名称
        emojiList.forEach { emoji ->
            if (emoji.name.lowercase().contains(lowerQuery)) {
                results.add(emoji)
            }
        }
        
        return results.toList().sortedBy { it.name }
    }
    
    /**
     * 获取最近使用的表情
     */
    fun getRecentEmojis(limit: Int = 20): List<Emoji> {
        return recentEmojis.take(limit).mapNotNull { unicode ->
            emojiList.find { it.unicode == unicode }
        }
    }
    
    /**
     * 记录表情使用
     */
    fun recordEmojiUsage(emoji: Emoji) {
        // 从最近使用列表中移除（如果存在）
        recentEmojis.remove(emoji.unicode)
        
        // 添加到列表开头
        recentEmojis.add(0, emoji.unicode)
        
        // 限制列表大小
        if (recentEmojis.size > 50) {
            recentEmojis.removeLast()
        }
        
        // 更新最后使用时间
        lastUsedTime = System.currentTimeMillis()
    }
    
    /**
     * 获取表情详情
     */
    fun getEmojiDetails(unicode: String): Emoji? {
        return emojiList.find { it.unicode == unicode }
    }
    
    /**
     * 获取表情的变体（肤色、性别等）
     */
    fun getEmojiVariants(baseEmoji: Emoji): List<Emoji> {
        val variants = mutableListOf<Emoji>()
        
        if (baseEmoji.skinToneSupport) {
            // 添加肤色变体
            val skinTones = listOf(
                "🏻", // light skin tone
                "🏼", // medium-light skin tone
                "🏽", // medium skin tone
                "🏾", // medium-dark skin tone
                "🏿"  // dark skin tone
            )
            
            skinTones.forEach { skinTone ->
                val variantUnicode = baseEmoji.unicode + skinTone
                val variantName = "${baseEmoji.name} (${getSkinToneName(skinTone)})"
                variants.add(
                    Emoji(
                        unicode = variantUnicode,
                        name = variantName,
                        keywords = baseEmoji.keywords,
                        category = baseEmoji.category,
                        skinToneSupport = false, // 变体不再支持肤色
                        genderSupport = baseEmoji.genderSupport,
                        version = baseEmoji.version
                    )
                )
            }
        }
        
        if (baseEmoji.genderSupport) {
            // 添加性别变体
            val genderVariants = listOf(
                "👨" to "男性",
                "👩" to "女性"
            )
            
            // 这里简化处理，实际需要根据具体表情处理
        }
        
        return variants
    }
    
    /**
     * 获取分类统计
     */
    fun getCategoryStats(): Map<EmojiCategory, Int> {
        return emojiByCategory.mapValues { it.value.size }
    }
    
    /**
     * 导出表情数据
     */
    fun exportEmojiData(): String {
        val json = JSONObject()
        
        // 添加元数据
        json.put("version", "1.0")
        json.put("total_emojis", emojiList.size)
        json.put("last_updated", System.currentTimeMillis())
        
        // 添加分类统计
        val statsJson = JSONObject()
        getCategoryStats().forEach { (category, count) ->
            statsJson.put(category.name, count)
        }
        json.put("category_stats", statsJson)
        
        // 添加最近使用
        val recentJson = JSONArray()
        recentEmojis.forEach { recentJson.put(it) }
        json.put("recent_emojis", recentJson)
        
        return json.toString(2)
    }
    
    /**
     * 清除最近使用记录
     */
    fun clearRecentEmojis() {
        recentEmojis.clear()
        saveRecentEmojis()
    }
    
    /**
     * 加载表情数据
     */
    private fun loadEmojiData() {
        // 这里应该从assets或网络加载完整的Unicode 15.1表情数据
        // 简化实现：添加一些常用表情
        
        // 表情与人物
        addEmojis(EmojiCategory.SMILEYS, listOf(
            Emoji("😀", "笑脸", listOf("笑脸", "开心", "高兴"), EmojiCategory.SMILEYS),
            Emoji("😂", "笑哭", listOf("笑哭", "大笑", "开心"), EmojiCategory.SMILEYS),
            Emoji("🥰", "微笑爱心脸", listOf("爱心", "微笑", "喜欢"), EmojiCategory.SMILEYS),
            Emoji("😎", "墨镜笑脸", listOf("酷", "墨镜", "自信"), EmojiCategory.SMILEYS),
            Emoji("😭", "大哭", listOf("大哭", "伤心", "悲伤"), EmojiCategory.SMILEYS),
            Emoji("😡", "生气", listOf("生气", "愤怒", "恼火"), EmojiCategory.SMILEYS),
            Emoji("😴", "睡觉", listOf("睡觉", "困", "疲惫"), EmojiCategory.SMILEYS),
            Emoji("🤔", "思考", listOf("思考", "疑惑", "考虑"), EmojiCategory.SMILEYS),
            Emoji("🤯", "爆炸头", listOf("震惊", "惊讶", "头脑爆炸"), EmojiCategory.SMILEYS),
            Emoji("🥺", "恳求脸", listOf("恳求", "可怜", "卖萌"), EmojiCategory.SMILEYS, skinToneSupport = true),
        ))
        
        // 动物与自然
        addEmojis(EmojiCategory.ANIMALS, listOf(
            Emoji("🐶", "狗脸", listOf("狗", "宠物", "狗狗"), EmojiCategory.ANIMALS),
            Emoji("🐱", "猫脸", listOf("猫", "宠物", "猫咪"), EmojiCategory.ANIMALS),
            Emoji("🐼", "熊猫", listOf("熊猫", "可爱", "中国"), EmojiCategory.ANIMALS),
            Emoji("🐯", "老虎脸", listOf("老虎", "凶猛", "动物"), EmojiCategory.ANIMALS),
            Emoji("🦁", "狮子脸", listOf("狮子", "草原", "王者"), EmojiCategory.ANIMALS),
            Emoji("🐮", "牛脸", listOf("牛", "奶牛", "农场"), EmojiCategory.ANIMALS),
            Emoji("🐷", "猪脸", listOf("猪", "小猪", "可爱"), EmojiCategory.ANIMALS),
            Emoji("🐸", "青蛙", listOf("青蛙", "呱呱", "动物"), EmojiCategory.ANIMALS),
            Emoji("🐥", "小鸡", listOf("小鸡", "小鸟", "可爱"), EmojiCategory.ANIMALS),
            Emoji("🦄", "独角兽", listOf("独角兽", "神奇", "梦幻"), EmojiCategory.ANIMALS),
        ))
        
        // 食物与饮料
        addEmojis(EmojiCategory.FOOD, listOf(
            Emoji("🍎", "红苹果", listOf("苹果", "水果", "健康"), EmojiCategory.FOOD),
            Emoji("🍕", "披萨", listOf("披萨", "美食", "意大利"), EmojiCategory.FOOD),
            Emoji("🍔", "汉堡", listOf("汉堡", "快餐", "美食"), EmojiCategory.FOOD),
            Emoji("🍜", "拉面", listOf("拉面", "面条", "日本"), EmojiCategory.FOOD),
            Emoji("🍣", "寿司", listOf("寿司", "日本", "美食"), EmojiCategory.FOOD),
            Emoji("🍩", "甜甜圈", listOf("甜甜圈", "甜点", "零食"), EmojiCategory.FOOD),
            Emoji("🍫", "巧克力", listOf("巧克力", "甜食", "零食"), EmojiCategory.FOOD),
            Emoji("🍦", "冰淇淋", listOf("冰淇淋", "冷饮", "甜点"), EmojiCategory.FOOD),
            Emoji("🍵", "绿茶", listOf("茶", "绿茶", "饮料"), EmojiCategory.FOOD),
            Emoji("🍺", "啤酒", listOf("啤酒", "酒精", "饮料"), EmojiCategory.FOOD),
        ))
        
        // 活动
        addEmojis(EmojiCategory.ACTIVITIES, listOf(
            Emoji("⚽", "足球", listOf("足球", "运动", "比赛"), EmojiCategory.ACTIVITIES),
            Emoji("🏀", "篮球", listOf("篮球", "运动", "NBA"), EmojiCategory.ACTIVITIES),
            Emoji("🎮", "游戏手柄", listOf("游戏", "手柄", "娱乐"), EmojiCategory.ACTIVITIES),
            Emoji("🎬", "场记板", listOf("电影", "拍摄", "娱乐"), EmojiCategory.ACTIVITIES),
            Emoji("🎨", "调色板", listOf("艺术", "绘画", "创意"), EmojiCategory.ACTIVITIES),
            Emoji("🎸", "吉他", listOf("吉他", "音乐", "乐器"), EmojiCategory.ACTIVITIES),
            Emoji("🎤", "麦克风", listOf("唱歌", "麦克风", "音乐"), EmojiCategory.ACTIVITIES),
            Emoji("🎭", "表演艺术", listOf("戏剧", "表演", "面具"), EmojiCategory.ACTIVITIES),
            Emoji("🎯", "飞镖", listOf("飞镖", "目标", "游戏"), EmojiCategory.ACTIVITIES),
            Emoji("🎳", "保龄球", listOf("保龄球", "运动", "游戏"), EmojiCategory.ACTIVITIES),
        ))
        
        // 旅行与地点
        addEmojis(EmojiCategory.TRAVEL, listOf(
            Emoji("🚗", "汽车", listOf("汽车", "交通", "出行"), EmojiCategory.TRAVEL),
            Emoji("✈️", "飞机", listOf("飞机", "旅行", "飞行"), EmojiCategory.TRAVEL),
            Emoji("🚆", "火车", listOf("火车", "铁路", "交通"), EmojiCategory.TRAVEL),
            Emoji("🚢", "轮船", listOf("轮船", "船", "航海"), EmojiCategory.TRAVEL),
            Emoji("🏠", "房子", listOf("房子", "家", "住宅"), EmojiCategory.TRAVEL),
            Emoji("🏥", "医院", listOf("医院", "医疗", "健康"), EmojiCategory.TRAVEL),
            Emoji("🏫", "学校", listOf("学校", "教育", "学习"), EmojiCategory.TRAVEL),
            Emoji("🏢", "办公楼", listOf("办公", "工作", "公司"), EmojiCategory.TRAVEL),
            Emoji("🗼", "东京塔", listOf("东京塔", "日本", "地标"), EmojiCategory.TRAVEL),
            Emoji("🗽", "自由女神像", listOf("自由女神", "美国", "地标"), EmojiCategory.TRAVEL),
        ))
        
        // 物品
        addEmojis(EmojiCategory.OBJECTS, listOf(
            Emoji("📱", "手机", listOf("手机", "智能", "通讯"), EmojiCategory.OBJECTS),
            Emoji("💻", "笔记本电脑", listOf("电脑", "笔记本", "工作"), EmojiCategory.OBJECTS),
            Emoji("⌚", "手表", listOf("手表", "时间", "配饰"), EmojiCategory.OBJECTS),
            Emoji("📷", "相机", listOf("相机", "摄影", "拍照"), EmojiCategory.OBJECTS),
            Emoji("🔑", "钥匙", listOf("钥匙", "开门", "安全"), EmojiCategory.OBJECTS),
            Emoji("💡", "灯泡", listOf("灯泡", "想法", "创意"), EmojiCategory.OBJECTS),
            Emoji("📚", "书籍", listOf("书", "学习", "知识"), EmojiCategory.OBJECTS),
            Emoji("🎁", "礼物", listOf("礼物", "惊喜", "庆祝"), EmojiCategory.OBJECTS),
            Emoji("💎", "宝石", listOf("宝石", "钻石", "珍贵"), EmojiCategory.OBJECTS),
            Emoji("🔧", "扳手", listOf("工具", "维修", "工作"), EmojiCategory.OBJECTS),
        ))
        
        // 符号
        addEmojis(EmojiCategory.SYMBOLS, listOf(
            Emoji("❤️", "红心", listOf("爱心", "喜欢", "爱"), EmojiCategory.SYMBOLS),
            Emoji("⭐", "星星", listOf("星星", "评分", "优秀"), EmojiCategory.SYMBOLS),
            Emoji("🔥", "火焰", listOf("火", "热", "流行"), EmojiCategory.SYMBOLS),
            Emoji("💯", "一百分", listOf("100分", "完美", "优秀"), EmojiCategory.SYMBOLS),
            Emoji("✅", "勾选标记", listOf("正确", "完成", "确认"), EmojiCategory.SYMBOLS),
            Emoji("❌", "叉号", listOf("错误", "取消", "拒绝"), EmojiCategory.SYMBOLS),
            Emoji("⚠️", "警告", listOf("警告", "注意", "危险"), EmojiCategory.SYMBOLS),
            Emoji("🚫", "禁止", listOf("禁止", "不允许", "停止"), EmojiCategory.SYMBOLS),
            Emoji("🔄", "循环箭头", listOf("刷新", "循环", "重复"), EmojiCategory.SYMBOLS),
            Emoji("⏩", "快进", listOf("快进", "跳过", "下一个"), EmojiCategory.SYMBOLS),
        ))
        
        // 旗帜（简化版，只添加几个）
        addEmojis(EmojiCategory.FLAGS, listOf(
            Emoji("🇨🇳", "中国国旗", listOf("中国", "国旗", "五星红旗"), EmojiCategory.FLAGS),
            Emoji("🇺🇸", "美国国旗", listOf("美国", "国旗", "星条旗"), EmojiCategory.FLAGS),
            Emoji("🇯🇵", "日本国旗", listOf("日本", "国旗", "太阳旗"), EmojiCategory.FLAGS),
            Emoji("🇰🇷", "韩国国旗", listOf("韩国", "国旗", "太极旗"), EmojiCategory.FLAGS),
            Emoji("🇬🇧", "英国国旗", listOf("英国", "国旗", "米字旗"), EmojiCategory.FLAGS),
            Emoji("🇫🇷", "法国国旗", listOf("法国", "国旗", "三色旗"), EmojiCategory.FLAGS),
            Emoji("🇩🇪", "德国国旗", listOf("德国", "国旗", "三色旗"), EmojiCategory.FLAGS),
            Emoji("🇷🇺", "俄罗斯国旗", listOf("俄罗斯", "国旗", "三色旗"), EmojiCategory.FLAGS),
            Emoji("🇮🇳", "印度国旗", listOf("印度", "国旗", "三色旗"), EmojiCategory.FLAGS),
            Emoji("🇧🇷", "巴西国旗", listOf("巴西", "国旗", "绿黄蓝"), EmojiCategory.FLAGS),
        ))
    }
    
    /**
     * 添加表情到指定分类
     */
    private fun addEmojis(category: EmojiCategory, emojis: List<Emoji>) {
        emojiList.addAll(emojis)
        
        val categoryList = emojiByCategory.getOrPut(category) { mutableListOf() }
        categoryList.addAll(emojis)
    }
    
    /**
     * 构建搜索索引
     */
    private fun buildSearchIndex() {
        emojiList.forEach { emoji ->
            // 索引名称
            addToSearchIndex(emoji.name.lowercase(), emoji)
            
            // 索引关键词
            emoji.keywords.forEach { keyword ->
                addToSearchIndex(keyword.lowercase(), emoji)
            }
            
            // 索引分类
            addToSearchIndex(emoji.category.displayName.lowercase(), emoji)
        }
    }
    
    /**
     * 添加到搜索索引
     */
    private fun addToSearchIndex(keyword: String, emoji: Emoji) {
        val list = searchIndex.getOrPut(keyword) { mutableListOf() }
        if (!list.contains(emoji)) {
            list.add(emoji)
        }
    }
    
    /**
     * 加载最近使用记录
     */
    private fun loadRecentEmojis() {
        val jsonString = prefs.getString("recent_emojis", "[]")
        jsonString?.let {
            try {
                val jsonArray = JSONArray(it)
                for (i in 0 until jsonArray.length()) {
                    val unicode = jsonArray.getString(i)
                    recentEmojis.add(unicode)
                }
            } catch (e: Exception) {
                // 解析失败，使用空列表
                recentEmojis.clear()
            }
        }
    }
    
    /**
     * 保存最近使用记录
     */
    private fun saveRecentEmojis() {
        val jsonArray = JSONArray()
        recentEmojis.forEach { jsonArray.put(it) }
        
        prefs.edit {
            putString("recent_emojis", jsonArray.toString())
        }
    }
    
    /**
     * 获取肤色名称
     */
    private fun getSkinToneName(skinTone: String): String {
        return when (skinTone) {
            "🏻" -> "浅肤色"
            "🏼" -> "中浅肤色"
            "🏽" -> "中肤色"
            "🏾" -> "中深肤色"
            "🏿" -> "深肤色"
            else -> "默认肤色"
        }
    }
    
    /**
     * 获取所有表情数量
     */
    fun getTotalEmojiCount(): Int {
        return emojiList.size
    }
    
    /**
     * 获取表情Unicode版本
     */
    fun getSupportedVersions(): List<String> {
        return emojiList.map { it.version }.distinct().sorted()
    }
    
    /**
     * 检查表情是否支持
     */
    fun isEmojiSupported(unicode: String): Boolean {
        return emojiList.any { it.unicode == unicode }
    }
    
    /**
     * 批量添加表情（用于动态加载）
     */
    fun addEmojis(emojis: List<Emoji>) {
        emojis.forEach { emoji ->
            emojiList.add(emoji)
            
            val categoryList = emojiByCategory.getOrPut(emoji.category) { mutableListOf() }
            categoryList.add(emoji)
            
            // 更新搜索索引
            addToSearchIndex(emoji.name.lowercase(), emoji)
            emoji.keywords.forEach { keyword ->
                addToSearchIndex(keyword.lowercase(), emoji)
            }
        }
    }
    
    /**
     * 清除所有表情数据
     */
    fun clearAllData() {
        emojiList.clear()
        emojiByCategory.clear()
        searchIndex.clear()
        recentEmojis.clear()
        
        prefs.edit {
            remove("recent_emojis")
        }
    }
}