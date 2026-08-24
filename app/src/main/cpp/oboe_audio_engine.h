#ifndef OBOE_AUDIO_ENGINE_H
#define OBOE_AUDIO_ENGINE_H

#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>

#define LOG_TAG "OboeNativeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class OboeAudioEngineCallback : public oboe::AudioStreamDataCallback {
public:
    OboeAudioEngineCallback(JNIEnv* env, jobject listener);
    ~OboeAudioEngineCallback();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames
    ) override;

private:
    JavaVM* javaVM;
    jobject callbackObj;
    jmethodID onBufferMethodId;
};

#endif // OBOE_AUDIO_ENGINE_H
