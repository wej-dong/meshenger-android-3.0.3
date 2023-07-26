//
// Created by Administrator on 2023/7/14.
//
#include <jni.h>

extern "C" {
JNIEXPORT jstring JNICALL
Java_com_example_myapp_MainActivity_getHelloString(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF("Hello from C++");
}
}