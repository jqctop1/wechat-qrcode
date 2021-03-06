/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include <string>
#include "opencv2/wechat_qrcode.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/imgproc.hpp"

#ifndef _Included_com_ocean_star_wechatscan_WeChatQRCodeDetector
#define _Included_com_ocean_star_wechatscan_WeChatQRCodeDetector
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_init
        (JNIEnv *, jobject, jstring, jstring, jstring, jstring);


JNIEXPORT jobjectArray JNICALL Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_detectRGB
        (JNIEnv *, jobject, jint, jintArray, jint, jint);

JNIEXPORT jobjectArray JNICALL Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_detectNV21
        (JNIEnv *, jobject, jint, jbyteArray, jint, jint);


JNIEXPORT void JNICALL Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_release
        (JNIEnv *, jobject, jint);

#ifdef __cplusplus
}
#endif
#endif
