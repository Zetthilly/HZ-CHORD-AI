#include "oboe_audio_engine.h"
#include <vector>
#include <cmath>

static oboe::AudioStream *recordingStream = nullptr;
static JavaVM *g_jvm = nullptr;
static jobject g_nativeCallback = nullptr;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

class MicroDataCallback : public oboe::AudioStreamDataCallback {
public:
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames
    ) override {
        if (!audioData || numFrames <= 0) return oboe::DataCallbackResult::Continue;

        auto *floatData = static_cast<float *>(audioData);

        if (g_jvm && g_nativeCallback) {
            JNIEnv *env = nullptr;
            int getEnvStat = g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
            bool isAttached = false;

            if (getEnvStat == JNI_EDETACHED) {
                if (g_jvm->AttachCurrentThread(&env, nullptr) == 0) {
                    isAttached = true;
                }
            }

            if (env && g_nativeCallback) {
                jclass clazz = env->GetObjectClass(g_nativeCallback);
                jmethodID method = env->GetMethodID(clazz, "onNativeAudioBuffer", "([FI)V");
                if (method) {
                    jfloatArray jBuffer = env->NewFloatArray(numFrames);
                    env->SetFloatArrayRegion(jBuffer, 0, numFrames, floatData);
                    env->CallVoidMethod(g_nativeCallback, method, jBuffer, numFrames);
                    env->DeleteLocalRef(jBuffer);
                }
            }

            if (isAttached) {
                g_jvm->DetachCurrentThread();
            }
        }

        return oboe::DataCallbackResult::Continue;
    }
};

static MicroDataCallback dataCallback;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_audio_OboeAudioEngine_startNativeStream(
    JNIEnv *env,
    jobject instance,
    jint sampleRate,
    jint channelCount,
    jobject callback
) {
    if (g_nativeCallback) {
        env->DeleteGlobalRef(g_nativeCallback);
        g_nativeCallback = nullptr;
    }
    if (callback) {
        g_nativeCallback = env->NewGlobalRef(callback);
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(channelCount)
        ->setSampleRate(sampleRate)
        ->setDataCallback(&dataCallback);

    oboe::Result result = builder.openStream(&recordingStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe native stream: %s", oboe::convertToText(result));
        return JNI_FALSE;
    }

    result = recordingStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe native stream: %s", oboe::convertToText(result));
        recordingStream->close();
        recordingStream = nullptr;
        return JNI_FALSE;
    }

    auto latencyResult = recordingStream->calculateLatencyMillis();
    double latencyMs = latencyResult ? latencyResult.value() : 0.0;
    LOGI("Oboe Native Stream started in Exclusive/LowLatency mode. Measured latency: %.2f ms", latencyMs);

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_stopNativeStream(
    JNIEnv *env,
    jobject instance
) {
    if (recordingStream) {
        recordingStream->requestStop();
        recordingStream->close();
        recordingStream = nullptr;
        LOGI("Oboe Native Stream stopped and closed.");
    }

    if (g_nativeCallback) {
        env->DeleteGlobalRef(g_nativeCallback);
        g_nativeCallback = nullptr;
    }
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngine_getMeasuredLatencyMs(
    JNIEnv *env,
    jobject instance
) {
    if (recordingStream) {
        auto result = recordingStream->calculateLatencyMillis();
        if (result) {
            return (jfloat) result.value();
        } else {
            // Buffer size in frames / sample rate
            int32_t bufferSize = recordingStream->getBufferSizeInFrames();
            int32_t sampleRate = recordingStream->getSampleRate();
            if (sampleRate > 0) {
                return ((float) bufferSize / (float) sampleRate) * 1000.0f;
            }
        }
    }
    return 0.0f;
}
