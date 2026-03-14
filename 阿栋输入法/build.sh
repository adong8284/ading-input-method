#!/bin/bash

# 阿栋输入法构建脚本
# 使用方法: ./build.sh [release|debug]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数定义
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境
check_environment() {
    print_info "检查构建环境..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        print_error "Java未安装，请安装JDK 11或更高版本"
        exit 1
    fi
    
    # 检查Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        print_warning "ANDROID_HOME未设置，尝试查找SDK..."
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
            print_info "找到Android SDK: $ANDROID_HOME"
        else
            print_error "未找到Android SDK，请设置ANDROID_HOME环境变量"
            exit 1
        fi
    fi
    
    # 检查Gradle
    if ! command -v ./gradlew &> /dev/null; then
        print_warning "Gradle Wrapper未找到，尝试下载..."
        chmod +x gradlew
    fi
    
    print_success "环境检查完成"
}

# 清理构建
clean_build() {
    print_info "清理构建文件..."
    ./gradlew clean
    print_success "清理完成"
}

# 构建调试版本
build_debug() {
    print_info "构建调试版本..."
    ./gradlew assembleDebug
    
    # 查找APK文件
    APK_FILE=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    if [ -f "$APK_FILE" ]; then
        print_success "调试版本构建完成: $APK_FILE"
        print_info "文件大小: $(du -h "$APK_FILE" | cut -f1)"
    else
        print_error "未找到APK文件"
        exit 1
    fi
}

# 构建发布版本
build_release() {
    print_info "构建发布版本..."
    
    # 检查密钥库
    if [ ! -f "keystore.jks" ]; then
        print_warning "未找到密钥库，创建新的密钥库..."
        
        # 提示用户输入信息
        echo "请输入密钥库信息："
        read -p "密钥库密码: " KEYSTORE_PASSWORD
        read -p "密钥别名: " KEY_ALIAS
        read -p "密钥密码: " KEY_PASSWORD
        read -p "姓名: " NAME
        read -p "组织单位: " ORGANIZATIONAL_UNIT
        read -p "组织: " ORGANIZATION
        read -p "城市: " CITY
        read -p "省份: " STATE
        read -p "国家代码: " COUNTRY_CODE
        
        # 创建密钥库
        keytool -genkey -v \
            -keystore keystore.jks \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -alias "$KEY_ALIAS" \
            -keypass "$KEY_PASSWORD" \
            -storepass "$KEYSTORE_PASSWORD" \
            -dname "CN=$NAME, OU=$ORGANIZATIONAL_UNIT, O=$ORGANIZATION, L=$CITY, ST=$STATE, C=$COUNTRY_CODE"
        
        # 保存密码到gradle.properties
        echo "KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD" >> gradle.properties
        echo "KEY_ALIAS=$KEY_ALIAS" >> gradle.properties
        echo "KEY_PASSWORD=$KEY_PASSWORD" >> gradle.properties
        
        print_success "密钥库创建完成"
    fi
    
    # 构建发布版本
    ./gradlew assembleRelease
    
    # 查找APK文件
    APK_FILE=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
    if [ -f "$APK_FILE" ]; then
        print_success "发布版本构建完成: $APK_FILE"
        print_info "文件大小: $(du -h "$APK_FILE" | cut -f1)"
        
        # 生成MD5校验和
        MD5_SUM=$(md5sum "$APK_FILE" | cut -d' ' -f1)
        echo "MD5: $MD5_SUM" > "${APK_FILE%.apk}.md5"
        print_info "MD5校验和已保存"
    else
        print_error "未找到APK文件"
        exit 1
    fi
}

# 安装到设备
install_apk() {
    print_info "查找设备..."
    
    # 获取设备列表
    DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
    
    if [ "$DEVICES" -eq 0 ]; then
        print_error "未找到连接的设备"
        print_info "请确保设备已连接并启用USB调试"
        exit 1
    fi
    
    # 选择APK文件
    if [ "$1" = "release" ]; then
        APK_FILE=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
    else
        APK_FILE=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    fi
    
    if [ ! -f "$APK_FILE" ]; then
        print_error "未找到APK文件，请先构建"
        exit 1
    fi
    
    print_info "安装APK: $APK_FILE"
    adb install -r "$APK_FILE"
    
    if [ $? -eq 0 ]; then
        print_success "安装成功"
        
        # 启动应用
        print_info "启动应用..."
        adb shell am start -n "com.ading.inputmethod/.SettingsActivity"
    else
        print_error "安装失败"
        exit 1
    fi
}

# 运行测试
run_tests() {
    print_info "运行单元测试..."
    ./gradlew testDebugUnitTest
    
    print_info "运行仪器测试..."
    ./gradlew connectedDebugAndroidTest
}

# 代码检查
check_code() {
    print_info "运行代码检查..."
    
    # Lint检查
    ./gradlew lintDebug
    
    # 代码格式化检查
    print_info "检查代码格式..."
    ./gradlew ktlintCheck
    
    # 依赖检查
    print_info "检查依赖更新..."
    ./gradlew dependencyUpdates
}

# 生成文档
generate_docs() {
    print_info "生成文档..."
    
    # 生成JavaDoc
    ./gradlew generateDebugJavaDoc
    
    # 复制文档到docs目录
    mkdir -p docs
    cp -r app/build/docs/javadoc/* docs/ 2>/dev/null || true
    
    print_success "文档已生成到docs目录"
}

# 清理缓存
clean_cache() {
    print_info "清理Gradle缓存..."
    ./gradlew cleanBuildCache
    
    print_info "清理Android构建缓存..."
    rm -rf ~/.gradle/caches/
    rm -rf ~/.android/build-cache/
    
    print_success "缓存清理完成"
}

# 显示帮助
show_help() {
    echo "阿栋输入法构建脚本"
    echo ""
    echo "使用方法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  debug       构建调试版本"
    echo "  release     构建发布版本（需要签名）"
    echo "  install     安装到设备"
    echo "  test        运行测试"
    echo "  check       代码检查"
    echo "  docs        生成文档"
    echo "  clean       清理构建"
    echo "  cache       清理缓存"
    echo "  all         执行完整构建流程"
    echo "  help        显示此帮助"
    echo ""
    echo "示例:"
    echo "  $0 debug      # 构建调试版本"
    echo "  $0 release    # 构建发布版本"
    echo "  $0 install    # 安装调试版本到设备"
}

# 主函数
main() {
    case "$1" in
        "debug")
            check_environment
            clean_build
            build_debug
            ;;
        "release")
            check_environment
            clean_build
            build_release
            ;;
        "install")
            check_environment
            install_apk "debug"
            ;;
        "test")
            check_environment
            run_tests
            ;;
        "check")
            check_environment
            check_code
            ;;
        "docs")
            check_environment
            generate_docs
            ;;
        "clean")
            clean_build
            ;;
        "cache")
            clean_cache
            ;;
        "all")
            check_environment
            clean_build
            build_debug
            run_tests
            check_code
            build_release
            generate_docs
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            print_error "未知命令: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
if [ $# -eq 0 ]; then
    show_help
    exit 0
fi

main "$1"