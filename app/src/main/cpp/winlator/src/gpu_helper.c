#include <jni.h>
#include <malloc.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>

#define VK_NO_PROTOTYPES 1
#include <vulkan/vulkan.h>
#include <EGL/egl.h>

#include "winlator.h"
#include "file_utils.h"

EGLContext globalEGLContext = EGL_NO_CONTEXT;

JNIEXPORT jobjectArray JNICALL
Java_com_winlator_core_GPUHelper_vkGetDeviceExtensions(JNIEnv *env, jclass obj) {
    PFN_vkCreateInstance vkCreateInstance = NULL;
    PFN_vkDestroyInstance vkDestroyInstance = NULL;
    PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = NULL;
    PFN_vkEnumerateDeviceExtensionProperties vkEnumerateDeviceExtensionProperties = NULL;
    void* libvulkan = NULL;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice* physicalDevices = NULL;
    VkExtensionProperties* properties = NULL;
    jobjectArray stringArray = NULL;
    VkInstanceCreateInfo createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;

    libvulkan = dlopen(LIBVULKAN_PATH, RTLD_NOW | RTLD_LOCAL);
    if (!libvulkan) goto done;

    vkCreateInstance = (PFN_vkCreateInstance)dlsym(libvulkan, "vkCreateInstance");
    vkDestroyInstance = (PFN_vkDestroyInstance)dlsym(libvulkan, "vkDestroyInstance");
    vkEnumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    vkEnumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)dlsym(libvulkan, "vkEnumerateDeviceExtensionProperties");
    if (!vkCreateInstance || !vkDestroyInstance || !vkEnumeratePhysicalDevices ||
        !vkEnumerateDeviceExtensionProperties) goto done;

    if (vkCreateInstance(&createInfo, NULL, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) goto done;

    uint32_t deviceCount = 0;
    VkResult result = vkEnumeratePhysicalDevices(instance, &deviceCount, NULL);
    if (result != VK_SUCCESS || deviceCount == 0) goto done;

    physicalDevices = calloc(deviceCount, sizeof(VkPhysicalDevice));
    if (!physicalDevices) goto done;

    result = vkEnumeratePhysicalDevices(instance, &deviceCount, physicalDevices);
    if (result != VK_SUCCESS || deviceCount == 0) goto done;

    uint32_t propertyCount = 0;
    result = vkEnumerateDeviceExtensionProperties(physicalDevices[0], NULL, &propertyCount, NULL);
    if (result != VK_SUCCESS || propertyCount == 0) goto done;

    properties = calloc(propertyCount, sizeof(VkExtensionProperties));
    if (!properties) goto done;

    result = vkEnumerateDeviceExtensionProperties(physicalDevices[0], NULL, &propertyCount, properties);
    if (result != VK_SUCCESS) goto done;

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    if (!stringClass) goto done;
    stringArray = (*env)->NewObjectArray(env, (jsize)propertyCount, stringClass, NULL);
    (*env)->DeleteLocalRef(env, stringClass);
    if (!stringArray) goto done;

    for (uint32_t i = 0; i < propertyCount; i++) {
        jstring value = (*env)->NewStringUTF(env, properties[i].extensionName);
        if (!value) {
            stringArray = NULL;
            goto done;
        }
        (*env)->SetObjectArrayElement(env, stringArray, (jsize)i, value);
        (*env)->DeleteLocalRef(env, value);
        if ((*env)->ExceptionCheck(env)) {
            stringArray = NULL;
            goto done;
        }
    }

done:
    if (instance != VK_NULL_HANDLE && vkDestroyInstance) vkDestroyInstance(instance, NULL);
    free(physicalDevices);
    free(properties);
    if (libvulkan) dlclose(libvulkan);
    if (!stringArray) {
        jclass stringClass = (*env)->FindClass(env, "java/lang/String");
        if (stringClass) {
            stringArray = (*env)->NewObjectArray(env, 0, stringClass, NULL);
            (*env)->DeleteLocalRef(env, stringClass);
        }
    }
    return stringArray;
}

JNIEXPORT jint JNICALL
Java_com_winlator_core_GPUHelper_vkGetApiVersion(void) {
    int version = 0;
    char* content = fileGetContents(APP_CACHE_DIR "/.vk-api-version", NULL, NULL);
    if (content) {
        version = strtol(content, NULL, 10);
        MEMFREE(content);
        if (version > 0) return version;
    }

    PFN_vkCreateInstance vkCreateInstance = NULL;
    PFN_vkDestroyInstance vkDestroyInstance = NULL;
    PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = NULL;
    PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties = NULL;
    void* libvulkan = NULL;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice* physicalDevices = NULL;
    VkInstanceCreateInfo createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;

    libvulkan = dlopen(LIBVULKAN_PATH, RTLD_NOW | RTLD_LOCAL);
    if (!libvulkan) goto done;

    vkCreateInstance = (PFN_vkCreateInstance)dlsym(libvulkan, "vkCreateInstance");
    vkDestroyInstance = (PFN_vkDestroyInstance)dlsym(libvulkan, "vkDestroyInstance");
    vkEnumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    vkGetPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)dlsym(libvulkan, "vkGetPhysicalDeviceProperties");
    if (!vkCreateInstance || !vkDestroyInstance || !vkEnumeratePhysicalDevices ||
        !vkGetPhysicalDeviceProperties) goto done;

    if (vkCreateInstance(&createInfo, NULL, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) goto done;

    uint32_t deviceCount = 0;
    VkResult result = vkEnumeratePhysicalDevices(instance, &deviceCount, NULL);
    if (result != VK_SUCCESS || deviceCount == 0) goto done;

    physicalDevices = calloc(deviceCount, sizeof(VkPhysicalDevice));
    if (!physicalDevices) goto done;

    result = vkEnumeratePhysicalDevices(instance, &deviceCount, physicalDevices);
    if (result != VK_SUCCESS || deviceCount == 0) goto done;

    VkPhysicalDeviceProperties properties = {0};
    vkGetPhysicalDeviceProperties(physicalDevices[0], &properties);
    version = (int)properties.apiVersion;


    {
        char valueText[32] = {0};
        snprintf(valueText, sizeof(valueText), "%d", version);
        filePutContents(APP_CACHE_DIR "/.vk-api-version", valueText, strlen(valueText));
    }

done:
    if (instance != VK_NULL_HANDLE && vkDestroyInstance) vkDestroyInstance(instance, NULL);
    free(physicalDevices);
    if (libvulkan) dlclose(libvulkan);
    return version;
}

JNIEXPORT void JNICALL
Java_com_winlator_core_GPUHelper_setGlobalEGLContext(JNIEnv *env, jclass obj) {
    globalEGLContext = eglGetCurrentContext();
}

JNIEXPORT jlong JNICALL
Java_com_winlator_core_GPUHelper_createOffscreenEGLContext(JNIEnv *env, jclass obj,
                                                           jboolean sharedContext) {
    static const EGLint confAttribList[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_NONE,
    };
    static const EGLint ctxAttribList[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    EGLBoolean success;

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) return 0;

    EGLint major, minor;
    success = eglInitialize(eglDisplay, &major, &minor);
    if (!success) return 0;

    int numConfigs = 0;
    EGLConfig eglConfig = NULL;
    success = eglChooseConfig(eglDisplay, confAttribList, &eglConfig, 1, &numConfigs);
    if (!success || numConfigs < 1 || eglConfig == NULL) {
        eglTerminate(eglDisplay);
        return 0;
    }

    EGLContext share = sharedContext ? globalEGLContext : EGL_NO_CONTEXT;
    EGLContext eglContext = eglCreateContext(eglDisplay, eglConfig, share, ctxAttribList);
    if (eglContext == EGL_NO_CONTEXT) {
        eglTerminate(eglDisplay);
        return 0;
    }

    success = eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext);
    if (!success) {
        eglDestroyContext(eglDisplay, eglContext);
        eglTerminate(eglDisplay);
        return 0;
    }
    return (jlong)eglContext;
}

JNIEXPORT void JNICALL
Java_com_winlator_core_GPUHelper_destroyOffscreenEGLContext(JNIEnv *env, jclass obj,
                                                            jlong contextPtr) {
    if (contextPtr == 0) return;
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(eglDisplay, (EGLContext)contextPtr);
}