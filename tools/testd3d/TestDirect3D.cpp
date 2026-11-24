#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <d3d9.h>
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdarg>
#include <cstring>

namespace {
constexpr char kWindowClass[] = "WinMaliDirect3D9TestWindow";
constexpr unsigned int kWarmupFrames = 24;
constexpr unsigned int kRequiredVerifiedFrames = 3;
constexpr DWORD kClearColor = D3DCOLOR_XRGB(20, 26, 38);

static HWND g_window = nullptr;
static IDirect3D9* g_d3d = nullptr;
static IDirect3DDevice9* g_device = nullptr;
static D3DPRESENT_PARAMETERS g_present = {};
static bool g_resetPending = false;
static bool g_errorReported = false;
static bool g_pixelVerificationUnavailable = false;
static bool g_passReported = false;
static unsigned int g_verifiedFrames = 0;
static unsigned long long g_frames = 0;

struct Vertex {
    float x;
    float y;
    float z;
    float rhw;
    D3DCOLOR color;
};

enum class PanelColor {
    RED,
    GREEN,
    BLUE,
    YELLOW
};

enum class VerifyResult {
    VERIFIED,
    VISUAL_FAILURE,
    UNAVAILABLE
};

void writeLog(const char* format, ...) {
    char tempPath[MAX_PATH] = {};
    const DWORD pathLength = GetTempPathA(MAX_PATH, tempPath);
    if (pathLength == 0 || pathLength >= MAX_PATH) return;

    char logPath[MAX_PATH] = {};
    const int written = snprintf(logPath, sizeof(logPath),
        "%sWinMali-TestDirect3D.log", tempPath);
    if (written <= 0 || static_cast<size_t>(written) >= sizeof(logPath)) return;

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

    char message[640] = {};
    snprintf(message, sizeof(message),
        "Direct3D 9 failed during %s.\n\nHRESULT: 0x%08lX\n\n"
        "See WinMali-TestDirect3D.log in the Windows temporary folder.",
        stage, static_cast<unsigned long>(result));

    writeLog("FAIL: %s; HRESULT=0x%08lX",
        stage, static_cast<unsigned long>(result));
    MessageBoxA(g_window, message, "WinMali Direct3D 9 - FAILED",
        MB_OK | MB_ICONERROR);
    PostQuitMessage(2);
}

void reportVisualFailure(const char* sampleName, DWORD pixel) {
    if (g_errorReported) return;
    g_errorReported = true;

    const DWORD red = (pixel >> 16) & 0xff;
    const DWORD green = (pixel >> 8) & 0xff;
    const DWORD blue = pixel & 0xff;

    char message[640] = {};
    snprintf(message, sizeof(message),
        "Direct3D calls completed, but the backbuffer did not contain "
        "the expected colour.\n\nSample: %s\nPixel: #%06lX "
        "(R=%lu G=%lu B=%lu)\n\n"
        "This is a rendering failure, not a readback-unsupported result. "
        "See WinMali-TestDirect3D.log.",
        sampleName, static_cast<unsigned long>(pixel & 0x00ffffff),
        static_cast<unsigned long>(red),
        static_cast<unsigned long>(green),
        static_cast<unsigned long>(blue));

    writeLog("FAIL: backbuffer colour mismatch at %s: #%06lX "
        "(R=%lu G=%lu B=%lu, frame=%llu)",
        sampleName, static_cast<unsigned long>(pixel & 0x00ffffff),
        static_cast<unsigned long>(red),
        static_cast<unsigned long>(green),
        static_cast<unsigned long>(blue), g_frames);

    MessageBoxA(g_window, message, "WinMali Direct3D 9 - BLACK/INVALID FRAME",
        MB_OK | MB_ICONERROR);
    PostQuitMessage(3);
}

void reportPixelReadbackUnavailable(const char* stage, HRESULT result) {
    if (g_pixelVerificationUnavailable) return;
    g_pixelVerificationUnavailable = true;

    writeLog("INCONCLUSIVE: pixel readback unavailable at %s; "
        "HRESULT=0x%08lX. D3D drawing and Present will continue.",
        stage, static_cast<unsigned long>(result));

    if (g_window) {
        SetWindowTextA(g_window,
            "Test Direct3D - INCONCLUSIVE (pixel readback unavailable)");
    }
}

bool isExpectedPanelColor(PanelColor expected, DWORD pixel) {
    const DWORD red = (pixel >> 16) & 0xff;
    const DWORD green = (pixel >> 8) & 0xff;
    const DWORD blue = pixel & 0xff;

    switch (expected) {
        case PanelColor::RED:
            return red >= 120 && red > green * 3 / 2 && red > blue * 3 / 2;
        case PanelColor::GREEN:
            return green >= 120 && green > red * 3 / 2 && green > blue * 6 / 5;
        case PanelColor::BLUE:
            return blue >= 120 && blue > red * 3 / 2 && blue > green * 6 / 5;
        case PanelColor::YELLOW:
            return red >= 130 && green >= 120 && blue < 120 &&
                red > blue * 3 / 2 && green > blue * 3 / 2;
        default:
            return false;
    }
}

bool readPixel(const D3DLOCKED_RECT& locked, const D3DSURFACE_DESC& desc,
        UINT x, UINT y, DWORD* outPixel) {
    if (!outPixel || !locked.pBits || locked.Pitch <= 0 ||
        x >= desc.Width || y >= desc.Height) return false;

    const size_t bytesPerPixel =
        desc.Format == D3DFMT_R5G6B5 ? sizeof(WORD) : sizeof(DWORD);
    const size_t minimumPitch = static_cast<size_t>(desc.Width) * bytesPerPixel;
    if (static_cast<size_t>(locked.Pitch) < minimumPitch) return false;

    const BYTE* row = static_cast<const BYTE*>(locked.pBits) +
        static_cast<size_t>(y) * static_cast<size_t>(locked.Pitch);

    if (desc.Format == D3DFMT_R5G6B5) {
        WORD packed = 0;
        memcpy(&packed, row + static_cast<size_t>(x) * sizeof(WORD), sizeof(packed));
        const DWORD red = ((packed >> 11) & 0x1f) * 255 / 31;
        const DWORD green = ((packed >> 5) & 0x3f) * 255 / 63;
        const DWORD blue = (packed & 0x1f) * 255 / 31;
        *outPixel = D3DCOLOR_XRGB(red, green, blue) & 0x00ffffff;
        return true;
    }

    DWORD packed = 0;
    memcpy(&packed, row + static_cast<size_t>(x) * sizeof(DWORD), sizeof(packed));
    *outPixel = packed & 0x00ffffff;
    return true;
}

VerifyResult verifyBackBufferPixels() {
    IDirect3DSurface9* backBuffer = nullptr;
    IDirect3DSurface9* staging = nullptr;

    HRESULT result = g_device->GetRenderTarget(0, &backBuffer);
    if (FAILED(result) || !backBuffer) {
        if (backBuffer) backBuffer->Release();
        reportPixelReadbackUnavailable("IDirect3DDevice9::GetRenderTarget",
            FAILED(result) ? result : E_FAIL);
        return VerifyResult::UNAVAILABLE;
    }

    D3DSURFACE_DESC desc = {};
    result = backBuffer->GetDesc(&desc);
    if (FAILED(result) || desc.Width < 16 || desc.Height < 16) {
        backBuffer->Release();
        reportPixelReadbackUnavailable("IDirect3DSurface9::GetDesc",
            FAILED(result) ? result : D3DERR_INVALIDCALL);
        return VerifyResult::UNAVAILABLE;
    }

    if (desc.Format != D3DFMT_X8R8G8B8 &&
        desc.Format != D3DFMT_A8R8G8B8 &&
        desc.Format != D3DFMT_R5G6B5) {
        backBuffer->Release();
        reportPixelReadbackUnavailable("unsupported backbuffer pixel format",
            D3DERR_NOTAVAILABLE);
        return VerifyResult::UNAVAILABLE;
    }

    result = g_device->CreateOffscreenPlainSurface(
        desc.Width, desc.Height, desc.Format, D3DPOOL_SYSTEMMEM, &staging, nullptr);
    if (FAILED(result) || !staging) {
        backBuffer->Release();
        if (staging) staging->Release();
        reportPixelReadbackUnavailable("CreateOffscreenPlainSurface",
            FAILED(result) ? result : E_FAIL);
        return VerifyResult::UNAVAILABLE;
    }

    result = g_device->GetRenderTargetData(backBuffer, staging);
    backBuffer->Release();
    if (FAILED(result)) {
        staging->Release();
        reportPixelReadbackUnavailable("IDirect3DDevice9::GetRenderTargetData", result);
        return VerifyResult::UNAVAILABLE;
    }

    D3DLOCKED_RECT locked = {};
    result = staging->LockRect(&locked, nullptr, D3DLOCK_READONLY);
    if (FAILED(result) || !locked.pBits || locked.Pitch <= 0) {
        staging->Release();
        reportPixelReadbackUnavailable("IDirect3DSurface9::LockRect",
            FAILED(result) ? result : D3DERR_INVALIDCALL);
        return VerifyResult::UNAVAILABLE;
    }

    struct Sample {
        const char* name;
        UINT x;
        UINT y;
        PanelColor expected;
    };

    const Sample samples[] = {
        {"top-left red panel", desc.Width / 4, desc.Height / 4, PanelColor::RED},
        {"top-right green panel", (desc.Width * 3) / 4, desc.Height / 4, PanelColor::GREEN},
        {"bottom-left blue panel", desc.Width / 4, (desc.Height * 3) / 4, PanelColor::BLUE},
        {"bottom-right yellow panel", (desc.Width * 3) / 4,
            (desc.Height * 3) / 4, PanelColor::YELLOW}
    };

    VerifyResult verification = VerifyResult::VERIFIED;
    for (const Sample& sample : samples) {
        DWORD pixel = 0;
        if (!readPixel(locked, desc, sample.x, sample.y, &pixel)) {
            reportPixelReadbackUnavailable("invalid locked-surface pitch or pixel address",
                D3DERR_INVALIDCALL);
            verification = VerifyResult::UNAVAILABLE;
            break;
        }
        if (!isExpectedPanelColor(sample.expected, pixel)) {
            reportVisualFailure(sample.name, pixel);
            verification = VerifyResult::VISUAL_FAILURE;
            break;
        }
    }

    const HRESULT unlockResult = staging->UnlockRect();
    staging->Release();

    if (verification == VerifyResult::VERIFIED && FAILED(unlockResult)) {
        reportPixelReadbackUnavailable("IDirect3DSurface9::UnlockRect", unlockResult);
        verification = VerifyResult::UNAVAILABLE;
    }

    if (verification == VerifyResult::VERIFIED) {
        ++g_verifiedFrames;
        writeLog("Verified four colour panels on frame %llu (%u/%u successful samples).",
            g_frames, g_verifiedFrames, kRequiredVerifiedFrames);
    }

    return verification;
}

HRESULT initializeFixedFunctionPipeline() {
    HRESULT result = g_device->SetVertexShader(nullptr);
    if (SUCCEEDED(result)) result = g_device->SetPixelShader(nullptr);
    if (SUCCEEDED(result)) result = g_device->SetTexture(0, nullptr);

    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_LIGHTING, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_ZENABLE, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_CULLMODE, D3DCULL_NONE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_ALPHABLENDENABLE, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_ALPHATESTENABLE, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_FOGENABLE, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_STENCILENABLE, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_SCISSORTESTENABLE, FALSE);
    if (SUCCEEDED(result)) result = g_device->SetRenderState(D3DRS_COLORWRITEENABLE,
        D3DCOLORWRITEENABLE_RED | D3DCOLORWRITEENABLE_GREEN |
        D3DCOLORWRITEENABLE_BLUE | D3DCOLORWRITEENABLE_ALPHA);

    if (SUCCEEDED(result)) result = g_device->SetTextureStageState(
        0, D3DTSS_COLOROP, D3DTOP_SELECTARG1);
    if (SUCCEEDED(result)) result = g_device->SetTextureStageState(
        0, D3DTSS_COLORARG1, D3DTA_DIFFUSE);
    if (SUCCEEDED(result)) result = g_device->SetTextureStageState(
        0, D3DTSS_ALPHAOP, D3DTOP_SELECTARG1);
    if (SUCCEEDED(result)) result = g_device->SetTextureStageState(
        0, D3DTSS_ALPHAARG1, D3DTA_DIFFUSE);
    if (SUCCEEDED(result)) result = g_device->SetTextureStageState(
        1, D3DTSS_COLOROP, D3DTOP_DISABLE);
    if (SUCCEEDED(result)) result = g_device->SetFVF(D3DFVF_XYZRHW | D3DFVF_DIFFUSE);

    return result;
}

HRESULT drawPanel(float left, float top, float right, float bottom, D3DCOLOR color) {
    const Vertex vertices[] = {
        {left,  top,    0.5f, 1.0f, color},
        {right, top,    0.5f, 1.0f, color},
        {left,  bottom, 0.5f, 1.0f, color},
        {right, bottom, 0.5f, 1.0f, color}
    };
    return g_device->DrawPrimitiveUP(D3DPT_TRIANGLESTRIP, 2,
        vertices, sizeof(Vertex));
}

bool drawTestPattern() {
    D3DVIEWPORT9 viewport = {};
    HRESULT result = g_device->GetViewport(&viewport);
    if (FAILED(result)) {
        reportFailure("IDirect3DDevice9::GetViewport", result);
        return false;
    }

    if (viewport.Width < 16 || viewport.Height < 16) {
        reportFailure("invalid Direct3D viewport", D3DERR_INVALIDCALL);
        return false;
    }

    result = g_device->Clear(0, nullptr, D3DCLEAR_TARGET, kClearColor, 1.0f, 0);
    if (FAILED(result)) {
        reportFailure("IDirect3DDevice9::Clear", result);
        return false;
    }

    result = g_device->BeginScene();
    if (FAILED(result)) {
        reportFailure("IDirect3DDevice9::BeginScene", result);
        return false;
    }

    const float width = static_cast<float>(viewport.Width);
    const float height = static_cast<float>(viewport.Height);
    const float shortest = std::min(width, height);
    const float margin = std::max(3.0f, shortest * 0.035f);
    const float gap = std::max(4.0f, shortest * 0.025f);
    const float middleX = width * 0.5f;
    const float middleY = height * 0.5f;
    const float left = margin;
    const float right = width - margin;
    const float top = margin;
    const float bottom = height - margin;
    const float leftPanelRight = middleX - gap * 0.5f;
    const float rightPanelLeft = middleX + gap * 0.5f;
    const float topPanelBottom = middleY - gap * 0.5f;
    const float bottomPanelTop = middleY + gap * 0.5f;

    result = drawPanel(left, top, leftPanelRight, topPanelBottom,
        D3DCOLOR_XRGB(240, 48, 48));
    if (SUCCEEDED(result)) result = drawPanel(rightPanelLeft, top, right, topPanelBottom,
        D3DCOLOR_XRGB(40, 224, 72));
    if (SUCCEEDED(result)) result = drawPanel(left, bottomPanelTop, leftPanelRight, bottom,
        D3DCOLOR_XRGB(48, 112, 240));
    if (SUCCEEDED(result)) result = drawPanel(rightPanelLeft, bottomPanelTop, right, bottom,
        D3DCOLOR_XRGB(236, 208, 40));

    const HRESULT endResult = g_device->EndScene();
    if (FAILED(result)) {
        reportFailure("Direct3D 9 colour-panel drawing", result);
        return false;
    }
    if (FAILED(endResult)) {
        reportFailure("IDirect3DDevice9::EndScene", endResult);
        return false;
    }

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
    if (!GetClientRect(window, &client)) {
        reportFailure("GetClientRect", HRESULT_FROM_WIN32(GetLastError()));
        return false;
    }

    ZeroMemory(&g_present, sizeof(g_present));
    g_present.Windowed = TRUE;
    g_present.SwapEffect = D3DSWAPEFFECT_DISCARD;
    g_present.BackBufferFormat = D3DFMT_UNKNOWN;
    g_present.BackBufferCount = 1;
    g_present.BackBufferWidth = static_cast<UINT>(
        std::max<LONG>(1, client.right - client.left));
    g_present.BackBufferHeight = static_cast<UINT>(
        std::max<LONG>(1, client.bottom - client.top));
    g_present.MultiSampleType = D3DMULTISAMPLE_NONE;
    g_present.MultiSampleQuality = 0;
    g_present.hDeviceWindow = window;
    g_present.PresentationInterval = D3DPRESENT_INTERVAL_DEFAULT;
    g_present.EnableAutoDepthStencil = FALSE;

    HRESULT result = g_d3d->CreateDevice(D3DADAPTER_DEFAULT, D3DDEVTYPE_HAL, window,
        D3DCREATE_SOFTWARE_VERTEXPROCESSING, &g_present, &g_device);
    if (FAILED(result) || !g_device) {
        reportFailure("IDirect3D9::CreateDevice (HAL, software vertex processing)",
            FAILED(result) ? result : E_FAIL);
        return false;
    }

    result = initializeFixedFunctionPipeline();
    if (FAILED(result)) {
        reportFailure("initial fixed-function pipeline setup", result);
        return false;
    }

    writeLog("D3D9 HAL device created (%ux%u, format UNKNOWN, no MSAA).",
        g_present.BackBufferWidth, g_present.BackBufferHeight);
    return true;
}

bool resetDevice() {
    if (!g_device || !g_window) return false;

    RECT client = {};
    if (!GetClientRect(g_window, &client)) {
        reportFailure("GetClientRect during device reset", HRESULT_FROM_WIN32(GetLastError()));
        return false;
    }

    g_present.BackBufferWidth = static_cast<UINT>(
        std::max<LONG>(1, client.right - client.left));
    g_present.BackBufferHeight = static_cast<UINT>(
        std::max<LONG>(1, client.bottom - client.top));

    const HRESULT result = g_device->Reset(&g_present);
    if (FAILED(result)) {
        if (result != D3DERR_DEVICELOST && result != D3DERR_DEVICENOTRESET) {
            reportFailure("IDirect3DDevice9::Reset", result);
        }
        return false;
    }

    const HRESULT pipelineResult = initializeFixedFunctionPipeline();
    if (FAILED(pipelineResult)) {
        reportFailure("fixed-function pipeline reinitialization after Reset",
            pipelineResult);
        return false;
    }

    g_resetPending = false;
    g_frames = 0;
    g_verifiedFrames = 0;
    g_pixelVerificationUnavailable = false;
    g_passReported = false;

    SetWindowTextA(g_window, "Test Direct3D - resized; checking renderer");
    writeLog("Device reset succeeded: %ux%u; visual verification restarted.",
        g_present.BackBufferWidth, g_present.BackBufferHeight);
    return true;
}

void renderFrame() {
    if (!g_device || g_errorReported || IsIconic(g_window)) return;
    if (g_resetPending) {
        resetDevice();
        return;
    }

    HRESULT result = g_device->TestCooperativeLevel();
    if (result == D3DERR_DEVICELOST) {
        SetWindowTextA(g_window, "Test Direct3D - device lost; waiting");
        return;
    }
    if (result == D3DERR_DEVICENOTRESET) {
        g_resetPending = true;
        resetDevice();
        return;
    }
    if (FAILED(result)) {
        reportFailure("IDirect3DDevice9::TestCooperativeLevel", result);
        return;
    }

    if (!drawTestPattern()) return;

    if (!g_pixelVerificationUnavailable &&
        g_verifiedFrames < kRequiredVerifiedFrames &&
        g_frames >= kWarmupFrames) {
        const VerifyResult verification = verifyBackBufferPixels();
        if (verification == VerifyResult::VISUAL_FAILURE) return;
    }

    result = g_device->Present(nullptr, nullptr, nullptr, nullptr);
    if (result == D3DERR_DEVICELOST) {
        SetWindowTextA(g_window, "Test Direct3D - device lost; waiting");
        return;
    }
    if (FAILED(result)) {
        reportFailure("IDirect3DDevice9::Present", result);
        return;
    }

    ++g_frames;

    if (!g_passReported && g_verifiedFrames >= kRequiredVerifiedFrames) {
        g_passReported = true;
        SetWindowTextA(g_window,
            "Test Direct3D - PASS (4 colour regions, 3 verified frames)");
        writeLog("PASS: four colour regions verified on %u consecutive frames; "
            "Present succeeded. Total frames=%llu.",
            kRequiredVerifiedFrames, g_frames);
    }
    else if (g_pixelVerificationUnavailable && g_frames == kWarmupFrames + 1) {
        SetWindowTextA(g_window,
            "Test Direct3D - INCONCLUSIVE (Present succeeds; pixel readback unavailable)");
    }
    else if (g_passReported && (g_frames % 120) == 0) {
        char title[160] = {};
        snprintf(title, sizeof(title),
            "Test Direct3D - PASS | %llu frames", g_frames);
        SetWindowTextA(g_window, title);
        writeLog("Present succeeded for %llu frames.", g_frames);
    }
}

LRESULT CALLBACK windowProcedure(HWND window, UINT message,
        WPARAM wParam, LPARAM lParam) {
    switch (message) {
        case WM_SIZE:
            if (g_device && wParam != SIZE_MINIMIZED) g_resetPending = true;
            return 0;

        case WM_GETMINMAXINFO: {
            MINMAXINFO* info = reinterpret_cast<MINMAXINFO*>(lParam);
            if (info) {
                info->ptMinTrackSize.x = 320;
                info->ptMinTrackSize.y = 240;
            }
            return 0;
        }

        case WM_ERASEBKGND:
            return 1;

        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;

        default:
            return DefWindowProcA(window, message, wParam, lParam);
    }
}

void releaseDirect3D() {
    if (g_device) {
        g_device->Release();
        g_device = nullptr;
    }
    if (g_d3d) {
        g_d3d->Release();
        g_d3d = nullptr;
    }
}
}

int WINAPI WinMain(HINSTANCE instance, HINSTANCE, LPSTR, int showCommand) {
    writeLog("Starting WinMali Direct3D 9 colour-pattern diagnostic.");
    WNDCLASSEXA windowClass = {};
    windowClass.cbSize = sizeof(windowClass);
    windowClass.style = CS_HREDRAW | CS_VREDRAW;
    windowClass.lpfnWndProc = windowProcedure;
    windowClass.hInstance = instance;
    windowClass.hCursor = LoadCursor(nullptr, IDC_ARROW);
    windowClass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    windowClass.lpszClassName = kWindowClass;

    if (!RegisterClassExA(&windowClass) &&
        GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
        writeLog("RegisterClassEx failed: %lu", GetLastError());
        MessageBoxA(nullptr, "Unable to register the Direct3D test window.",
            "WinMali Direct3D 9 - FAILED", MB_OK | MB_ICONERROR);
        return 1;
    }

    g_window = CreateWindowExA(WS_EX_APPWINDOW, kWindowClass,
        "Test Direct3D - initializing",
        WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 800, 600,
        nullptr, nullptr, instance, nullptr);

    if (!g_window) {
        writeLog("CreateWindowEx failed: %lu", GetLastError());
        MessageBoxA(nullptr, "Unable to create the Direct3D test window.",
            "WinMali Direct3D 9 - FAILED", MB_OK | MB_ICONERROR);
        return 1;
    }

    ShowWindow(g_window, showCommand);
    UpdateWindow(g_window);

    if (!createDevice(g_window)) {
        releaseDirect3D();
        return 2;
    }

    SetWindowTextA(g_window, "Test Direct3D - warming up renderer");
    writeLog("Window and D3D9 device initialized; warming up for %u frames.",
        kWarmupFrames);

    MSG message = {};
    bool running = true;
    while (running) {
        while (PeekMessageA(&message, nullptr, 0, 0, PM_REMOVE)) {
            if (message.message == WM_QUIT) {
                running = false;
                break;
            }
            TranslateMessage(&message);
            DispatchMessageA(&message);
        }
        if (!running) break;

        renderFrame();
        Sleep(16);
    }

    writeLog("Exited after %llu successfully presented frames; "
        "pixel-verified frames=%u; readback-unavailable=%s.",
        g_frames, g_verifiedFrames,
        g_pixelVerificationUnavailable ? "yes" : "no");
    releaseDirect3D();
    return static_cast<int>(message.wParam);
}
