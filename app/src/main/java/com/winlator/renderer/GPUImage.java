package com.winlator.renderer;

import androidx.annotation.Keep;

import com.winlator.xserver.Drawable;
import com.winlator.xserver.XServer;

import java.nio.ByteBuffer;

public class GPUImage extends Texture {
    private long hardwareBufferPtr;
    private long imageKHRPtr;
    private ByteBuffer virtualData;
    private short stride;
    private boolean locked = false;
    private int nativeHandle;

    static {
        System.loadLibrary("winlator");
    }

    public GPUImage(Drawable owner) {
        this(owner, true, true);
    }

    public GPUImage(Drawable owner, boolean cpuAccess) {
        this(owner, cpuAccess, true);
    }

    public GPUImage(Drawable owner, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        super(owner);
        hardwareBufferPtr = createHardwareBuffer(owner.width, owner.height,
            GPUImageBufferUsage.forCpuAccess(cpuAccess), useHALPixelFormatBGRA8888);
        if (hardwareBufferPtr == 0) {
            android.util.Log.e("WinMali-GPUImage", "Unable to allocate the shared Android hardware buffer");
            return;
        }
        if (cpuAccess) {
            virtualData = lockHardwareBuffer(hardwareBufferPtr);
            if (virtualData != null) locked = true;
            else {
                android.util.Log.e("WinMali-GPUImage", "Unable to lock the CPU-accessible hardware buffer");
                destroyHardwareBuffer(hardwareBufferPtr, false);
                hardwareBufferPtr = 0;
            }
        }
    }

    @Override
    public void allocateTexture(short width, short height, ByteBuffer data) {
        if (isAllocated() || hardwareBufferPtr == 0) return;
        super.allocateTexture(width, height, null);
        imageKHRPtr = createImageKHR(hardwareBufferPtr, textureId);
        if (imageKHRPtr == 0) {
            super.destroy();
            android.util.Log.e("WinMali-GPUImage", "EGLImage import failed; the shared window texture was not created");
        }
    }

    @Override
    public void updateFromDrawable() {
        if (!isAllocated() && owner != null) allocateTexture(owner.width, owner.height, null);
        needsUpdate = false;
    }

    public short getStride() {
        return stride;
    }

    @Keep
    private void setStride(short stride) {
        this.stride = stride;
    }

    public int getNativeHandle() {
        return nativeHandle;
    }

    @Keep
    private void setNativeHandle(int nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public ByteBuffer getVirtualData() {
        return virtualData;
    }

    @Override
    public void destroy() {
        if (imageKHRPtr != 0) destroyImageKHR(imageKHRPtr, hardwareBufferPtr);
        destroyHardwareBuffer(hardwareBufferPtr, locked);
        virtualData = null;
        imageKHRPtr = 0;
        hardwareBufferPtr = 0;
        super.destroy();
    }

    public long getHardwareBufferPtr() {
        return hardwareBufferPtr;
    }

    public static GPUImage createOrObtain(XServer xServer, Drawable drawable, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        final Texture texture = drawable.getTexture();
        if (!(texture instanceof GPUImage)) {
            GPUImage gpuImage = new GPUImage(drawable, cpuAccess, useHALPixelFormatBGRA8888);
            if (gpuImage.getHardwareBufferPtr() == 0) {
                gpuImage.destroy();
                android.util.Log.e("WinMali-GPUImage", "Keeping the existing drawable texture because shared-buffer allocation failed");
                return null;
            }
            xServer.getRenderer().xServerView.queueEvent(texture::destroy);
            drawable.setTexture(gpuImage);
            return gpuImage;
        }
        else return (GPUImage)texture;
    }

    private native long createHardwareBuffer(short width, short height, long usage, boolean useHALPixelFormatBGRA8888);

    private native void destroyHardwareBuffer(long hardwareBufferPtr, boolean locked);

    private native ByteBuffer lockHardwareBuffer(long hardwareBufferPtr);

    private native long createImageKHR(long hardwareBufferPtr, int textureId);

    private native void destroyImageKHR(long imageKHRPtr, long hardwareBufferPtr);
}