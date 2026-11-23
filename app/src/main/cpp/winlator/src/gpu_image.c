#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/native_window.h>

#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <jni.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <sys/mman.h>

#include "native_handle.h"
#include "winlator.h"

#define HAL_PIXEL_FORMAT_BGRA_8888 5
#define WINMALI_GPU_IMAGE_LOG "WinMali-GPUImage"

extern const native_handle_t* _Nullable AHardwareBuffer_getNativeHandle(const AHardwareBuffer* _Nonnull buffer);

int AHardwareBuffer_getFd(AHardwareBuffer* hardwareBuffer) {
    if (hardwareBuffer == NULL) return -1;
    const native_handle_t* nativeHandle = AHardwareBuffer_getNativeHandle(hardwareBuffer);
    if (nativeHandle == NULL || nativeHandle->numFds <= 0) return -1;
    return nativeHandle->data[0];
}

EGLImageKHR createImageKHR(AHardwareBuffer* hardwareBuffer, int textureId) {
    if (hardwareBuffer == NULL || textureId <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "EGLImage creation received a null buffer or invalid texture id");
        return EGL_NO_IMAGE_KHR;
    }

    const EGLint attribList[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    EGLDisplay eglDisplay = eglGetCurrentDisplay();
    if (eglDisplay == EGL_NO_DISPLAY) eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "No EGL display is available: 0x%x", eglGetError());
        return EGL_NO_IMAGE_KHR;
    }

    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(hardwareBuffer);
    if (clientBuffer == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "eglGetNativeClientBufferANDROID failed: 0x%x", eglGetError());
        return EGL_NO_IMAGE_KHR;
    }

    AHardwareBuffer_acquire(hardwareBuffer);
    EGLImageKHR imageKHR = eglCreateImageKHR(
        eglDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribList);
    if (imageKHR == EGL_NO_IMAGE_KHR) {
        EGLint error = eglGetError();
        AHardwareBuffer_release(hardwareBuffer);
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "eglCreateImageKHR failed: 0x%x", error);
        return EGL_NO_IMAGE_KHR;
    }

    // Clear stale GL errors so the following error belongs to this import.
    for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) {}
    glBindTexture(GL_TEXTURE_2D, (GLuint)textureId);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, imageKHR);
    GLenum importError = glGetError();
    glBindTexture(GL_TEXTURE_2D, 0);
    GLenum unbindError = glGetError();

    if (importError != GL_NO_ERROR || unbindError != GL_NO_ERROR) {
        eglDestroyImageKHR(eglDisplay, imageKHR);
        AHardwareBuffer_release(hardwareBuffer);
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "glEGLImageTargetTexture2DOES failed: 0x%x (unbind 0x%x)",
            importError, unbindError);
        return EGL_NO_IMAGE_KHR;
    }

    // The extra AHardwareBuffer reference is released by destroyImageKHR().
    return imageKHR;
}

AHardwareBuffer* createHardwareBuffer(int width, int height, uint64_t usage,
                                      bool useHALPixelFormatBGRA8888) {
    if (width <= 0 || height <= 0 || usage == 0) return NULL;

    AHardwareBuffer_Desc buffDesc = {0};
    buffDesc.width = (uint32_t)width;
    buffDesc.height = (uint32_t)height;
    buffDesc.layers = 1;
    buffDesc.usage = usage;
    buffDesc.format = useHALPixelFormatBGRA8888 ?
        HAL_PIXEL_FORMAT_BGRA_8888 : AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    AHardwareBuffer* hardwareBuffer = NULL;
    int result = AHardwareBuffer_allocate(&buffDesc, &hardwareBuffer);
    if (result != 0 || hardwareBuffer == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "AHardwareBuffer_allocate failed: %d (%dx%d usage=0x%llx)",
            result, width, height, (unsigned long long)usage);
        return NULL;
    }
    return hardwareBuffer;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_renderer_GPUImage_createHardwareBuffer(JNIEnv *env, jobject obj,
        jshort width, jshort height, jlong usage, jboolean useHALPixelFormatBGRA8888) {
    AHardwareBuffer* hardwareBuffer = createHardwareBuffer(
        width, height, (uint64_t)usage, useHALPixelFormatBGRA8888);
    if (hardwareBuffer == NULL) return 0;

    jclass cls = (*env)->GetObjectClass(env, obj);
    AHardwareBuffer_Desc buffDesc = {0};
    AHardwareBuffer_describe(hardwareBuffer, &buffDesc);

    if (cls != NULL) {
        jmethodID setStride = (*env)->GetMethodID(env, cls, "setStride", "(S)V");
        if (setStride != NULL) {
            (*env)->CallVoidMethod(env, obj, setStride, (jshort)buffDesc.stride);
        }
        int fd = AHardwareBuffer_getFd(hardwareBuffer);
        if (fd != -1) {
            jmethodID setNativeHandle = (*env)->GetMethodID(env, cls, "setNativeHandle", "(I)V");
            if (setNativeHandle != NULL) {
                (*env)->CallVoidMethod(env, obj, setNativeHandle, fd);
            }
        }
    }

    if ((*env)->ExceptionCheck(env)) {
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "Failed to publish Android hardware-buffer metadata to GPUImage");
        (*env)->ExceptionClear(env);
        AHardwareBuffer_release(hardwareBuffer);
        return 0;
    }
    return (jlong)hardwareBuffer;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_renderer_GPUImage_createImageKHR(JNIEnv *env, jobject obj,
        jlong hardwareBufferPtr, jint textureId) {
    return (jlong)createImageKHR((AHardwareBuffer*)hardwareBufferPtr, textureId);
}

JNIEXPORT void JNICALL
Java_com_winlator_renderer_GPUImage_destroyHardwareBuffer(JNIEnv *env, jobject obj,
        jlong hardwareBufferPtr, jboolean locked) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (hardwareBuffer == NULL) return;

    if (locked) {
        int result = AHardwareBuffer_unlock(hardwareBuffer, NULL);
        if (result != 0) {
            __android_log_print(ANDROID_LOG_WARN, WINMALI_GPU_IMAGE_LOG,
                "AHardwareBuffer_unlock failed: %d", result);
        }
    }
    AHardwareBuffer_release(hardwareBuffer);
}

JNIEXPORT jobject JNICALL
Java_com_winlator_renderer_GPUImage_lockHardwareBuffer(JNIEnv *env, jobject obj,
        jlong hardwareBufferPtr) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (hardwareBuffer == NULL) return NULL;

    void *virtualAddr = NULL;
    int result = AHardwareBuffer_lock(
        hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, NULL, &virtualAddr);
    if (result != 0 || virtualAddr == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "AHardwareBuffer_lock failed: %d", result);
        return NULL;
    }

    AHardwareBuffer_Desc buffDesc = {0};
    AHardwareBuffer_describe(hardwareBuffer, &buffDesc);
    jlong size = (jlong)buffDesc.stride * (jlong)buffDesc.height * 4;
    jobject buffer = (*env)->NewDirectByteBuffer(env, virtualAddr, size);
    if (buffer == NULL || (*env)->ExceptionCheck(env)) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        AHardwareBuffer_unlock(hardwareBuffer, NULL);
        __android_log_print(ANDROID_LOG_ERROR, WINMALI_GPU_IMAGE_LOG,
            "Unable to create the Java direct buffer for the hardware buffer");
        return NULL;
    }
    return buffer;
}

JNIEXPORT void JNICALL
Java_com_winlator_renderer_GPUImage_destroyImageKHR(JNIEnv *env, jobject obj,
        jlong imageKHRPtr, jlong hardwareBufferPtr) {
    EGLImageKHR imageKHR = (EGLImageKHR)imageKHRPtr;
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (imageKHR != EGL_NO_IMAGE_KHR) {
        EGLDisplay eglDisplay = eglGetCurrentDisplay();
        if (eglDisplay == EGL_NO_DISPLAY) eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (eglDisplay != EGL_NO_DISPLAY && !eglDestroyImageKHR(eglDisplay, imageKHR)) {
            __android_log_print(ANDROID_LOG_WARN, WINMALI_GPU_IMAGE_LOG,
                "eglDestroyImageKHR failed: 0x%x", eglGetError());
        }
    }
    // Balance the acquire performed after obtaining the native EGL client buffer.
    if (hardwareBuffer != NULL && imageKHR != EGL_NO_IMAGE_KHR) {
        AHardwareBuffer_release(hardwareBuffer);
    }
}
