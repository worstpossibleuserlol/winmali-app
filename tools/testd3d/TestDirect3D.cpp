#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <d3d9.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdarg>

namespace {
constexpr char kWindowClass[] = "WinMaliDirect3D9TestWindow";
static HWND g_window = nullptr;
static IDirect3D9* g_d3d = nullptr;
static IDirect3DDevice9* g_device = nullptr;
static D3DPRESENT_PARAMETERS g_present = {};
static bool g_resetPending = false;
static bool g_errorReported = false;
static bool g_firstFrameVerified = false;
static unsigned long long g_frames = 0;
static DWORD g_startTick = 0;
struct Vertex { float x, y, z, rhw; D3DCOLOR color; };

void writeLog(const char* format, ...) {
    char tempPath[MAX_PATH] = {};
    DWORD pathLength = GetTempPathA(MAX_PATH, tempPath);
    if (pathLength == 0 || pathLength >= MAX_PATH) return;
    char logPath[MAX_PATH] = {};
    if (snprintf(logPath, sizeof(logPath), "%sWinMali-TestDirect3D.log", tempPath) <= 0) return;
    FILE* file = fopen(logPath, "ab");
    if (!file) return;
    va_list args;
    va_start(args, format);
    vfprintf(file, format, args);
    va_end(args);
    fputc('\n', file);
    fclose(file);
}

void reportFailure(const char* stage, HRESULT result) {
    if (g_errorReported) return;
    g_errorReported = true;
    char message[512] = {};
    snprintf(message, sizeof(message),
        "Direct3D 9 test failed during %s.\n\nHRESULT: 0x%08lX\n\n"
        "See WinMali-TestDirect3D.log in the Windows temporary folder.",
        stage, static_cast<unsigned long>(result));
    writeLog("%s failed: HRESULT=0x%08lX", stage, static_cast<unsigned long>(result));
    MessageBoxA(g_window, message, "WinMali Test Direct3D - FAILED", MB_OK | MB_ICONERROR);
    PostQuitMessage(2);
}

bool resetDevice() {
    if (!g_device || !g_window) return false;
    RECT client = {};
    if (!GetClientRect(g_window, &client)) return false;
    g_present.BackBufferWidth = static_cast<UINT>(std::max<LONG>(1, client.right - client.left));
    g_present.BackBufferHeight = static_cast<UINT>(std::max<LONG>(1, client.bottom - client.top));
    HRESULT result = g_device->Reset(&g_present);
    if (FAILED(result)) {
        if (result != D3DERR_DEVICELOST && result != D3DERR_DEVICENOTRESET)
            reportFailure("IDirect3DDevice9::Reset", result);
        return false;
    }
    g_resetPending = false;
    writeLog("Device reset succeeded: %ux%u", g_present.BackBufferWidth, g_present.BackBufferHeight);
    return true;
}

bool createDevice(HWND window) {
    g_d3d = Direct3DCreate9(D3D_SDK_VERSION);
    if (!g_d3d) {
        writeLog("Direct3DCreate9 returned NULL");
        reportFailure("Direct3DCreate9", E_FAIL);
        return false;
    }
    RECT client = {};
    GetClientRect(window, &client);
    ZeroMemory(&g_present, sizeof(g_present));
    g_present.Windowed = TRUE;
    g_present.SwapEffect = D3DSWAPEFFECT_DISCARD;
    g_present.BackBufferFormat = D3DFMT_UNKNOWN;
    g_present.BackBufferCount = 1;
    g_present.BackBufferWidth = static_cast<UINT>(std::max<LONG>(1, client.right - client.left));
    g_present.BackBufferHeight = static_cast<UINT>(std::max<LONG>(1, client.bottom - client.top));
    g_present.MultiSampleType = D3DMULTISAMPLE_NONE;
    g_present.hDeviceWindow = window;
    g_present.PresentationInterval = D3DPRESENT_INTERVAL_IMMEDIATE;

    HRESULT result = g_d3d->CreateDevice(D3DADAPTER_DEFAULT, D3DDEVTYPE_HAL, window,
        D3DCREATE_SOFTWARE_VERTEXPROCESSING, &g_present, &g_device);
    if (FAILED(result) || !g_device) {
        reportFailure("IDirect3D9::CreateDevice (HAL, software vertex processing)",
            FAILED(result) ? result : E_FAIL);
        return false;
    }
    result = g_device->SetRenderState(D3DRS_LIGHTING, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_CULLMODE, D3DCULL_NONE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_ZENABLE, FALSE);
    if (FAILED(result)) {
        reportFailure("initial Direct3D render-state setup", result);
        return false;
    }
    writeLog("D3D9 HAL device created.");
    return true;
}

void reportVisualFailure(DWORD pixel) {
    if (g_errorReported) return;
    g_errorReported = true;
    char message[512] = {};
    snprintf(message, sizeof(message),
        "Direct3D calls completed, but the rendered triangle was not found in the backbuffer.\n\n"
        "Center pixel: #%06lX\n\n"
        "The frame may be black or contain only the clear colour. "
        "See WinMali-TestDirect3D.log in the Windows temporary folder.",
        static_cast<unsigned long>(pixel & 0x00ffffff));
    writeLog("Backbuffer pixel verification failed: #%06lX",
        static_cast<unsigned long>(pixel & 0x00ffffff));
    MessageBoxA(g_window, message, "WinMali Test Direct3D - FAILED", MB_OK | MB_ICONERROR);
    PostQuitMessage(3);
}

bool verifyBackBufferPixels() {
    IDirect3DSurface9* backBuffer = nullptr;
    IDirect3DSurface9* staging = nullptr;
    HRESULT result = g_device->GetRenderTarget(0, &backBuffer);
    if (FAILED(result) || !backBuffer) {
        reportFailure("IDirect3DDevice9::GetRenderTarget", FAILED(result) ? result : E_FAIL);
        return false;
    }

    D3DSURFACE_DESC desc = {};
    result = backBuffer->GetDesc(&desc);
    if (FAILED(result) || desc.Width < 4 || desc.Height < 4) {
        backBuffer->Release();
        reportFailure("IDirect3DSurface9::GetDesc", FAILED(result) ? result : D3DERR_INVALIDCALL);
        return false;
    }

    if (desc.Format != D3DFMT_X8R8G8B8 &&
        desc.Format != D3DFMT_A8R8G8B8 &&
        desc.Format != D3DFMT_R5G6B5) {
        backBuffer->Release();
        reportFailure("backbuffer pixel format (unsupported for readback)", D3DERR_NOTAVAILABLE);
        return false;
    }

    result = g_device->CreateOffscreenPlainSurface(
        desc.Width, desc.Height, desc.Format, D3DPOOL_SYSTEMMEM, &staging, nullptr);
    if (FAILED(result) || !staging) {
        backBuffer->Release();
        reportFailure("CreateOffscreenPlainSurface for pixel verification",
            FAILED(result) ? result : E_FAIL);
        return false;
    }

    result = g_device->GetRenderTargetData(backBuffer, staging);
    backBuffer->Release();
    if (FAILED(result)) {
        staging->Release();
        reportFailure("IDirect3DDevice9::GetRenderTargetData", result);
        return false;
    }

    D3DLOCKED_RECT locked = {};
    result = staging->LockRect(&locked, nullptr, D3DLOCK_READONLY);
    if (FAILED(result) || !locked.pBits || locked.Pitch <= 0) {
        staging->Release();
        reportFailure("IDirect3DSurface9::LockRect for pixel verification",
            FAILED(result) ? result : D3DERR_INVALIDCALL);
        return false;
    }

    const UINT x = desc.Width / 2;
    const UINT y = desc.Height / 2;
    const BYTE* row = static_cast<const BYTE*>(locked.pBits) +
        static_cast<size_t>(y) * static_cast<size_t>(locked.Pitch);
    DWORD pixel = 0;
    if (desc.Format == D3DFMT_R5G6B5) {
        const WORD packed = reinterpret_cast<const WORD*>(row)[x];
        const DWORD red = ((packed >> 11) & 0x1f) * 255 / 31;
        const DWORD green = ((packed >> 5) & 0x3f) * 255 / 63;
        const DWORD blue = (packed & 0x1f) * 255 / 31;
        pixel = D3DCOLOR_XRGB(red, green, blue) & 0x00ffffff;
    } else {
        pixel = reinterpret_cast<const DWORD*>(row)[x] & 0x00ffffff;
    }

    HRESULT unlockResult = staging->UnlockRect();
    staging->Release();
    if (FAILED(unlockResult)) {
        reportFailure("IDirect3DSurface9::UnlockRect after pixel verification", unlockResult);
        return false;
    }

    const DWORD red = (pixel >> 16) & 0xff;
    const DWORD green = (pixel >> 8) & 0xff;
    const DWORD blue = pixel & 0xff;
    // The centre is the triangle's centroid. Its interpolated vertex colour
    // should be bright; black output and the dark clear colour both fail.
    if (red < 50 || green < 70 || blue < 70 || red + green + blue < 300) {
        reportVisualFailure(pixel);
        return false;
    }

    g_firstFrameVerified = true;
    writeLog("Backbuffer pixel verified at (%u,%u): #%06lX",
        x, y, static_cast<unsigned long>(pixel));
    return true;
}

void renderFrame() {
    if (!g_device || g_errorReported || IsIconic(g_window)) return;
    if (g_resetPending) { resetDevice(); return; }
    HRESULT result = g_device->TestCooperativeLevel();
    if (result == D3DERR_DEVICELOST) return;
    if (result == D3DERR_DEVICENOTRESET) { g_resetPending = true; resetDevice(); return; }
    if (FAILED(result)) { reportFailure("TestCooperativeLevel", result); return; }

    result = g_device->Clear(0, nullptr, D3DCLEAR_TARGET, D3DCOLOR_XRGB(18, 34, 58), 1.0f, 0);
    if (FAILED(result)) { reportFailure("IDirect3DDevice9::Clear", result); return; }
    result = g_device->BeginScene();
    if (FAILED(result)) { reportFailure("IDirect3DDevice9::BeginScene", result); return; }

    D3DVIEWPORT9 viewport = {};
    result = g_device->GetViewport(&viewport);
    if (FAILED(result)) {
        g_device->EndScene();
        reportFailure("IDirect3DDevice9::GetViewport", result);
        return;
    }
    const float width = static_cast<float>(viewport.Width);
    const float height = static_cast<float>(viewport.Height);
    const float radius = std::max(24.0f, std::min(width, height) * 0.27f);
    const float cx = width * 0.5f, cy = height * 0.5f;
    const float angle = static_cast<float>(GetTickCount() - g_startTick) * 0.0008f;
    const float localX[3] = {radius, std::cos(2.0943951f) * radius, std::cos(4.1887902f) * radius};
    const float localY[3] = {0.0f, std::sin(2.0943951f) * radius, std::sin(4.1887902f) * radius};
    const D3DCOLOR colors[3] = {
        D3DCOLOR_XRGB(255, 82, 82), D3DCOLOR_XRGB(82, 230, 145), D3DCOLOR_XRGB(80, 170, 255)
    };
    Vertex vertices[3] = {};
    for (int i = 0; i < 3; ++i) {
        const float x = localX[i] * std::cos(angle) - localY[i] * std::sin(angle);
        const float y = localX[i] * std::sin(angle) + localY[i] * std::cos(angle);
        vertices[i] = {cx + x, cy + y, 0.5f, 1.0f, colors[i]};
    }
    result = g_device->SetFVF(D3DFVF_XYZRHW | D3DFVF_DIFFUSE);
    if (SUCCEEDED(result)) result = g_device->DrawPrimitiveUP(D3DPT_TRIANGLELIST, 1, vertices, sizeof(Vertex));
    HRESULT endResult = g_device->EndScene();
    if (FAILED(result)) { reportFailure("Direct3D 9 triangle draw", result); return; }
    if (FAILED(endResult)) { reportFailure("IDirect3DDevice9::EndScene", endResult); return; }
    if (!g_firstFrameVerified && !verifyBackBufferPixels()) return;

    result = g_device->Present(nullptr, nullptr, nullptr, nullptr);
    if (result == D3DERR_DEVICELOST) return;
    if (FAILED(result)) { reportFailure("IDirect3DDevice9::Present", result); return; }
    ++g_frames;
    if (g_frames == 1) {
        SetWindowTextA(g_window, "Test Direct3D - PASS (D3D9 pixels verified)");
        writeLog("First frame presented successfully.");
    } else if ((g_frames % 120) == 0) {
        char title[128] = {};
        snprintf(title, sizeof(title), "Test Direct3D - PASS | %llu frames", g_frames);
        SetWindowTextA(g_window, title);
        writeLog("Frames presented: %llu", g_frames);
    }
}

LRESULT CALLBACK windowProcedure(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
        case WM_SIZE:
            if (g_device && wParam != SIZE_MINIMIZED) g_resetPending = true;
            return 0;
        case WM_ERASEBKGND: return 1;
        case WM_DESTROY: PostQuitMessage(0); return 0;
        default: return DefWindowProcA(window, message, wParam, lParam);
    }
}

void releaseDirect3D() {
    if (g_device) { g_device->Release(); g_device = nullptr; }
    if (g_d3d) { g_d3d->Release(); g_d3d = nullptr; }
}
}

int WINAPI WinMain(HINSTANCE instance, HINSTANCE, LPSTR, int showCommand) {
    writeLog("Starting source-built WinMali Direct3D 9 diagnostic.");
    g_startTick = GetTickCount();
    WNDCLASSEXA windowClass = {};
    windowClass.cbSize = sizeof(windowClass);
    windowClass.style = CS_HREDRAW | CS_VREDRAW;
    windowClass.lpfnWndProc = windowProcedure;
    windowClass.hInstance = instance;
    windowClass.hCursor = LoadCursor(nullptr, IDC_ARROW);
    windowClass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    windowClass.lpszClassName = kWindowClass;
    if (!RegisterClassExA(&windowClass) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
        writeLog("RegisterClassEx failed: %lu", GetLastError());
        MessageBoxA(nullptr, "Unable to register the Direct3D test window.",
            "WinMali Test Direct3D - FAILED", MB_OK | MB_ICONERROR);
        return 1;
    }
    g_window = CreateWindowExA(WS_EX_APPWINDOW, kWindowClass,
        "WinMali Test Direct3D - Initializing", WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT, 800, 600, nullptr, nullptr, instance, nullptr);
    if (!g_window) {
        writeLog("CreateWindowEx failed: %lu", GetLastError());
        MessageBoxA(nullptr, "Unable to create the Direct3D test window.",
            "WinMali Test Direct3D - FAILED", MB_OK | MB_ICONERROR);
        return 1;
    }
    ShowWindow(g_window, showCommand);
    UpdateWindow(g_window);
    if (!createDevice(g_window)) { releaseDirect3D(); return 2; }
    SetWindowTextA(g_window, "Test Direct3D - Device created; testing Present");
    writeLog("Window and D3D9 device initialized.");

    MSG message = {};
    bool running = true;
    while (running) {
        while (PeekMessageA(&message, nullptr, 0, 0, PM_REMOVE)) {
            if (message.message == WM_QUIT) { running = false; break; }
            TranslateMessage(&message);
            DispatchMessageA(&message);
        }
        if (!running) break;
        renderFrame();
        Sleep(16);
    }
    writeLog("Exited after %llu successfully presented frames.", g_frames);
    releaseDirect3D();
    return static_cast<int>(message.wParam);
}
