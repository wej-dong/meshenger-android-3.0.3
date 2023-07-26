LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# 提供要生成的共享库的名称
LOCAL_MODULE    := hello
# 声明原生源代码文件的名称
LOCAL_SRC_FILES := hello.cpp

# 引入外部库，供构建系统在构建二进制文件时使用
# D:\Environment\SDK\ndk\25.1.8937393\toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib
# LOCAL_LDLIBS    := -llog -landroid -lEGL -lGLESv1_CM

include $(BUILD_SHARED_LIBRARY)