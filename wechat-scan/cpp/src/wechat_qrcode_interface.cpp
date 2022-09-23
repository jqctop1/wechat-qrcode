
#include "wechat_qrcode_interface.h"
#include <android/log.h>
#include <map>

#define TAG "wechat_qrcode"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace cv;
using namespace cv::wechat_qrcode;

extern "C" {

    std::map<int, Ptr<WeChatQRCode>> detectors;
    int instance_id = 0;
    jclass result_class;
    jclass rect_class;
    jmethodID result_method;
    jmethodID rect_method;

    JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
        JNIEnv* env = NULL;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            return JNI_ERR;
        }
        result_class = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("com/ocean/star/wechatscan/WeChatQRCodeDetector$Result")));
        rect_class = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("android/graphics/Rect")));
        result_method = env->GetMethodID(result_class, "<init>","(Ljava/lang/String;Landroid/graphics/Rect;)V");
        rect_method = env->GetMethodID(rect_class, "<init>", "(IIII)V");
        return JNI_VERSION_1_6;
    }

    JNIEXPORT jint JNICALL
    Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_init(JNIEnv* env, jobject obj,
                                                                      jstring detectProto,
                                                                      jstring detectModel,
                                                                      jstring srProto,
                                                                      jstring srModel) {

        const char* detectProtoPath = env->GetStringUTFChars(detectProto, nullptr);
        const char* detectModelPath = env->GetStringUTFChars(detectModel, nullptr);
        const char* srProtoPath = env->GetStringUTFChars(srProto, nullptr);
        const char* srModelPath = env->GetStringUTFChars(srModel, nullptr);

        LOGI("detectProto %s, detectModel %s, srProto %s, srModel %s", detectProtoPath, detectModelPath, srProtoPath,srModelPath);

       Ptr<WeChatQRCode> wechat_qrcode_ptr;
        try {
            wechat_qrcode_ptr = makePtr<WeChatQRCode>(detectProtoPath, detectModelPath, srProtoPath, srModelPath);
            instance_id++;
            detectors.insert(std::pair<int, Ptr<WeChatQRCode>>(instance_id, wechat_qrcode_ptr));
            env->ReleaseStringUTFChars(detectProto, detectProtoPath);
            env->ReleaseStringUTFChars(detectModel, detectModelPath);
            env->ReleaseStringUTFChars(srProto, srProtoPath);
            env->ReleaseStringUTFChars(srModel, srModelPath);
        } catch (const std::exception &e) {
            LOGE("init occur exception %s", e.what());
            return -1;
        }
        return instance_id;
    }

    Ptr<WeChatQRCode> findDetector(int id) {
        std::map<int, Ptr<WeChatQRCode>>::iterator itera = detectors.find(id);
        if (itera != detectors.end()) {
            return itera->second;
        }
        return nullptr;
    }

    Mat rgb2Mat(const jint* rgb, jint width, jint height) {
        uint len = width * height;
        LOGI("width %d, height %d", width, height);
        uchar* data = (uchar *) malloc(len * 3);
        for (int i = 0; i < len; i++) {
            uint pixel = (uint) rgb[i];
            data[i] = pixel & 0xFFu;
            data[i + 1] = (pixel >> 8u) & 0xFFu;
            data[i + 2] = (pixel >> 16u) & 0xFFu;
        }
        return Mat(height, width, CV_8UC3, data);
    }

    Mat yuv2Mat(const jbyte* yuv, jint width, jint height) {
        uint len = width * height * 3 / 2;
        LOGI("width %d, height %d, len %d", width, height, len);
        uchar* data = (uchar *) malloc(len);
        memcpy(data, yuv, len);
        Mat yuvMat = Mat(3 * height / 2, width, CV_8UC1, data);
        return yuvMat;
    }

    jobjectArray getDecodeResults(JNIEnv *env, std::vector<std::string> results, std::vector<Mat> points) {
        jobjectArray result_array = env->NewObjectArray(results.size(), result_class, nullptr);
        if (!results.empty()) {
            std::vector<std::string>::iterator str_itera = results.begin();
            std::vector<Mat>::iterator mat_itera = points.begin();
            int index = 0;
            while (str_itera != results.end() && mat_itera != points.end()) {
                const char* result_cstr = str_itera->c_str();
                jstring result_string = env->NewStringUTF(result_cstr);
                int left = (int) mat_itera->at<float>(0,0);
                int top = (int) mat_itera->at<float>(0, 1);
                int right = (int) mat_itera->at<float>(2, 0);
                int bottom = (int) mat_itera->at<float>(2, 1);
                LOGI("result %s", result_cstr);
                LOGI("rect %d, %d, %d, %d", left, top, right, bottom);
                jobject result_rect = env->NewObject(rect_class, rect_method, left, top, right, bottom);
                jobject result = env->NewObject(result_class, result_method, result_string, result_rect);
                env->SetObjectArrayElement(result_array, index++, result);
                str_itera++;
                mat_itera++;
            }
        }
        return result_array;
    }

    JNIEXPORT jobjectArray JNICALL
    Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_detectRGB(JNIEnv* env, jobject, jint id, jintArray rgb, jint width, jint height) {
        jobjectArray result_array;
        try {
            Ptr<WeChatQRCode> detector = findDetector(id);
            if (detector != nullptr) {
                TickMeter tick;
                tick.start();
                jint* rgbData = env->GetIntArrayElements(rgb, nullptr);
                Mat img = rgb2Mat(rgbData, width, height);
                env->ReleaseIntArrayElements(rgb, rgbData, JNI_ABORT);
                std::vector<Mat> points;
                std::vector<std::string> results = detector->detectAndDecode(img, points);
                tick.stop();
                LOGI("detectRGB, result size %d, cost time %f ms", results.size(), tick.getTimeMilli());
                result_array = getDecodeResults(env, results, points);
            }
        } catch (const std::exception &e) {
            LOGE("detect  occur exception %s", e.what());
        }
        return result_array;
    }

    JNIEXPORT jobjectArray JNICALL
    Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_detectNV21(JNIEnv* env, jobject, jint id, jbyteArray yuv, jint width, jint height) {
        jobjectArray result_array;
        try {
            Ptr<WeChatQRCode> detector = findDetector(id);
            if (detector != nullptr) {
                TickMeter tick;
                tick.start();
                jbyte* yuvData = env->GetByteArrayElements(yuv, nullptr);
                Mat img = yuv2Mat(yuvData, width, height);
                env->ReleaseByteArrayElements(yuv, yuvData, JNI_ABORT);
                std::vector<Mat> points;
                std::vector<std::string> results = detector->detectAndDecode(img, points);
                tick.stop();
                LOGI("detectNV21, result size %d, cost time %f ms", results.size(), tick.getTimeMilli());
                result_array = getDecodeResults(env, results, points);
            }
        } catch (const std::exception &e) {
            LOGE("detect  occur exception %s", e.what());
        }
        return result_array;
    }


    JNIEXPORT void JNICALL
    Java_com_ocean_star_wechatscan_WeChatQRCodeDetector_release(JNIEnv* env, jobject, jint id) {
        try {
            std::map<int, Ptr<WeChatQRCode>>::iterator itera = detectors.find(id);
            if (itera != detectors.end()) {
                itera->second.release();
                detectors.erase(itera);
            }
        } catch (const std::exception &e) {
            LOGE("release %d occur exception %s", id, e.what());
        }
    }

}
