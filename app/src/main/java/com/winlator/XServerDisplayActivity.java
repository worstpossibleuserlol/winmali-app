package com.winlator;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.alsaserver.ALSAClient;
import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.ActiveWindowsDialog;
import com.winlator.contentdialog.AudioDriverConfigDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.DXVKConfigDialog;
import com.winlator.contentdialog.DebugDialog;
import com.winlator.contentdialog.ScreenEffectDialog;
import com.winlator.contentdialog.TurnipConfigDialog;
import com.winlator.contentdialog.VKD3DConfigDialog;
import com.winlator.contentdialog.VirGLConfigDialog;
import com.winlator.contentdialog.WineD3DConfigDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;
import com.winlator.core.GPUHelper;
import com.winlator.core.GpuCompatibilityProfile;
import com.winlator.core.CompatibilityCenter;
import com.winlator.core.GraphicsDriverInstallState;
import com.winlator.core.DXWrapperInstallState;
import com.winlator.core.WinComponentInstallState;
import com.winlator.core.KeyValueSet;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.Win32AppWorkarounds;
import com.winlator.core.WineInfo;
import com.winlator.core.WineInstaller;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineStartMenuCreator;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.math.Mathf;
import com.winlator.renderer.GLRenderer;
import com.winlator.services.ForegroundService;
import com.winlator.widget.FrameRating;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.MagnifierView;
import com.winlator.widget.TouchpadView;
import com.winlator.widget.XServerView;
import com.winlator.winhandler.TaskManagerDialog;
import com.winlator.winhandler.WinHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.ALSAServerComponent;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.xenvironment.components.NetworkInfoUpdateComponent;
import com.winlator.xenvironment.components.PulseAudioComponent;
import com.winlator.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.xenvironment.components.VirGLRendererComponent;
import com.winlator.xenvironment.components.VortekRendererComponent;
import com.winlator.xenvironment.components.XServerComponent;
import com.winlator.xserver.Atom;
import com.winlator.xserver.Property;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;

public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private Container container;
    private ContainerManager containerManager;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private RootFS rootFS;
    private FrameRating frameRating;
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    private String[] graphicsDriver = {GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER};
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private ScreenInfo screenInfo = new ScreenInfo(Container.DEFAULT_SCREEN_SIZE);
    private KeyValueSet[] dxwrapperConfig;
    private KeyValueSet[] graphicsDriverConfig = {new KeyValueSet(), new KeyValueSet()};
    private KeyValueSet audioDriverConfig;
    private String wincomponents;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private EnvVars overrideEnvVars;
    private ClipboardManager clipboardManager;
    private SharedPreferences preferences;
    private final WinHandler winHandler = new WinHandler(this);
    private float globalCursorSpeed = 1.0f;
    private boolean capturePointerOnExternalMouse = true;
    private MagnifierView magnifierView;
    private DebugDialog debugDialog;
    public int frameRatingWindowId = -1;
    private Win32AppWorkarounds win32AppWorkarounds;
    private String screenEffectProfile;
    private final java.util.concurrent.atomic.AtomicBoolean exitRequested = new java.util.concurrent.atomic.AtomicBoolean(false);
    private boolean automaticGpuSettingsEnabled;
    private GpuCompatibilityProfile.Vendor activeGpuVendor = GpuCompatibilityProfile.Vendor.UNKNOWN;
    private String gameProfileBox64Preset;
    private EnvVars gameProfileEnvVars;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
        setContentView(R.layout.xserver_display_activity);
        ForegroundService.startSession(this);

        final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useAndroidClipboardOnWine = preferences.getBoolean("use_android_clipboard_on_wine", false);
        clipboardManager = useAndroidClipboardOnWine ? (ClipboardManager)getSystemService(CLIPBOARD_SERVICE) : null;

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        NavigationView navigationView = findViewById(R.id.NavigationView);
        ProcessHelper.removeAllDebugCallbacks();
        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false) || preferences.getInt("box64_logs", 0) >= 1;
        if (enableLogs) ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.menu_item_logs).setVisible(enableLogs);
        navigationView.setNavigationItemSelectedListener(this);

        rootFS = RootFS.find(this);

        if (!isGenerateWineprefix()) {
            containerManager = new ContainerManager(this);
            container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));
            if (container == null || !containerManager.activateContainer(container)) {
                finish();
                return;
            }

            boolean wineprefixNeedsUpdate = container.getExtra("wineprefixNeedsUpdate").equals("t");
            if (wineprefixNeedsUpdate) {
                preloaderDialog.show(R.string.updating_system_files);
                WineUtils.updateWineprefix(this, (status) -> {
                    if (status == 0) {
                        container.putExtra("wineprefixNeedsUpdate", null);
                        container.putExtra("wincomponents", null);
                        container.saveData();
                        AppUtils.restartActivity(this);
                    }
                    else finish();
                });
                return;
            }

            win32AppWorkarounds = new Win32AppWorkarounds(this);

            String wineVersion = container.getWineVersion();
            wineInfo = WineInfo.fromIdentifier(this, wineVersion);

            if (wineInfo != WineInfo.MAIN_WINE_INFO) rootFS.setWinePath(wineInfo.path);

            String shortcutPath = getIntent().getStringExtra("shortcut_path");
            if (shortcutPath != null && !shortcutPath.isEmpty()) shortcut = new Shortcut(container, new File(shortcutPath));

            String graphicsDriver = container.getGraphicsDriver();
            audioDriver = container.getAudioDriver();
            String dxwrapper = container.getDXWrapper();
            wincomponents = container.getWinComponents();
            String dxwrapperConfig = container.getDXWrapperConfig();
            String graphicsDriverConfig = container.getGraphicsDriverConfig();
            audioDriverConfig = new KeyValueSet(container.getAudioDriverConfig());
            screenInfo = resolveScreenInfo(container.getScreenSize());

            if (shortcut != null) {
                graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
                audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
                dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
                wincomponents = shortcut.getExtra("wincomponents", container.getWinComponents());
                dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
                graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
                audioDriverConfig = new KeyValueSet(shortcut.getExtra("audioDriverConfig", container.getAudioDriverConfig()));
                screenInfo = resolveScreenInfo(shortcut.getExtra("screenSize", container.getScreenSize()));

                CompatibilityCenter.GameProfile gameProfile =
                    CompatibilityCenter.getGameProfile(this, container, shortcut.name, shortcut.path);
                if (gameProfile != null) {
                    dxwrapper = gameProfile.dxWrapper;
                    dxwrapperConfig = "";
                    gameProfileBox64Preset = gameProfile.box64Preset;
                    gameProfileEnvVars = new EnvVars(gameProfile.envVars);
                    android.util.Log.i("WinMali-Compatibility",
                        "Applied launch-only compatibility profile for " + shortcut.name);
                }

                String dinputMapperType = shortcut.getExtra("dinputMapperType");
                if (!dinputMapperType.isEmpty()) winHandler.gamepadHandler.setDInputMapperType(Byte.parseByte(dinputMapperType));

                win32AppWorkarounds.applyStartupWorkarounds(!shortcut.wmClass.isEmpty() ? shortcut.wmClass : shortcut.path);
            }
            else {
                Intent intent = getIntent();
                if (intent.hasExtra("exec_path")) win32AppWorkarounds.applyStartupWorkarounds(FileUtils.getName(intent.getStringExtra("exec_path")));
            }

            this.graphicsDriver = GraphicsDrivers.parseIdentifiers(graphicsDriver);
            this.graphicsDriverConfig = GraphicsDrivers.parseConfigs(graphicsDriver, graphicsDriverConfig);
            this.dxwrapper = DXWrappers.parseIdentifier(dxwrapper);
            this.dxwrapperConfig = DXWrappers.parseConfigs(dxwrapper, dxwrapperConfig);
        }

        preloaderDialog.show(R.string.starting_up);

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(this, screenInfo);
        xServer.setWinHandler(winHandler);
        final boolean[] flags = {false, shortcut != null || getIntent().hasExtra("exec_path")};
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (window.id == frameRatingWindowId) frameRating.update();
            }

            @Override
            public void onMapWindow(Window window) {
                if (!flags[0] && window.isRenderable() && !window.getClassName().isEmpty()) {
                    updateCursorVisibility();
                    preloaderDialog.closeOnUiThread();
                    flags[0] = true;
                }

                if (flags[1] && window.attributes.isViewable() && window.isDesktopWindow()) {
                    window.attributes.setViewable(false);
                    if (window.attributes.isEnabled()) window.disableAllDescendants();
                }

                if (win32AppWorkarounds != null) win32AppWorkarounds.applyWindowWorkarounds(window);
                changeFrameRatingVisibility(window, true);
            }

            @Override
            public void onUnmapWindow(Window window) {
                changeFrameRatingVisibility(window, false);
            }
        });

        setupUI();

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean systemFilesReady = true;
            if (!isGenerateWineprefix()) {
                applyAutomaticGpuCompatibilityFallback();
                systemFilesReady = setupWineSystemFiles();
                if (systemFilesReady) {
                    systemFilesReady = extractGraphicsDriverFiles();
                    if (systemFilesReady) changeWineAudioDriver();
                }
            }

            if (systemFilesReady) {
                setupXEnvironment();
            }
            else {
                runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(this, R.string.unable_to_install_system_files);
                    finish();
                });
            }
        });
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            if (capturePointerOnExternalMouse) {
                // Captured events are delivered to the focused view.
                View captureView = inputControlsView.getVisibility() == View.VISIBLE ? inputControlsView : touchpadView;
                if (captureView.requestFocus()) captureView.requestPointerCapture();
            }

            if (winHandler != null && clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                ClipData primaryClip = clipboardManager.getPrimaryClip();
                if (primaryClip != null && primaryClip.getItemCount() > 0) {
                    winHandler.setClipboardData(primaryClip.getItemAt(0).getText().toString());
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        ForegroundService.onResumeSession(this);
    }

    @Override
    public void onPause() {
        ForegroundService.onPauseSession(this);
        super.onPause();
        if (environment != null && !isInPictureInPictureMode()) {
            environment.onPause();
            xServerView.onPause();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        ForegroundService.setPipMode(isInPictureInPictureMode);
    }

    @Override
    protected void onDestroy() {
        winHandler.stop();
        if (environment != null) environment.stopEnvironmentComponents();
        if (containerManager != null && container != null) containerManager.deactivateContainer(container);
        ForegroundService.stopSession(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            else drawerLayout.closeDrawers();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        final GLRenderer renderer = xServerView.getRenderer();
        switch (item.getItemId()) {
            case R.id.menu_item_keyboard:
                AppUtils.showKeyboard(this);
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_input_controls:
                showInputControlsDialog();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_toggle_fullscreen:
                renderer.toggleFullscreen();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_task_manager:
                (new TaskManagerDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_active_windows:
                (new ActiveWindowsDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_magnifier:
                if (magnifierView == null) {
                    final FrameLayout container = findViewById(R.id.FLXServerDisplay);
                    magnifierView = new MagnifierView(this);
                    magnifierView.setZoomButtonCallback((value) -> {
                        renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                        magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    });
                    magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    magnifierView.setHideButtonCallback(() -> {
                        container.removeView(magnifierView);
                        magnifierView = null;
                    });
                    container.addView(magnifierView);
                }
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_screen_effect:
                (new ScreenEffectDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_pip_mode:
                PictureInPictureParams pipParams = (new PictureInPictureParams.Builder())
                    .setAspectRatio(screenInfo.aspectRatio())
                    .build();
                enterPictureInPictureMode(pipParams);
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_logs:
                debugDialog.show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_touchpad_help:
                showTouchpadHelpDialog();
                break;
            case R.id.menu_item_exit:
                exit();
                break;
        }
        return true;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    private void exit() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread(this::exit);
            return;
        }
        if (!exitRequested.compareAndSet(false, true)) return;

        winHandler.stop();
        if (environment != null) environment.stopEnvironmentComponents();

        Intent intent = getIntent();
        if (intent.hasExtra("exec_path")) {
            AppUtils.RestartApplicationOptions options = new AppUtils.RestartApplicationOptions();
            options.containerId = container.id;
            options.startPath = FileUtils.getDirname(intent.getStringExtra("exec_path"));
            AppUtils.restartApplication(this, options);
        }
        else AppUtils.restartApplication(this);
        ForegroundService.stopSession(this);
    }

    /**
     * Re-check automatic Mali settings at launch so containers created by an
     * older WinMali build do not keep an unsafe automatic DXVK/Turnip selection.
     * Explicitly manual container settings are left untouched.
     */
    private void applyAutomaticGpuCompatibilityFallback() {
        if (container == null) return;

        String savedAutoSetting = container.getExtra("autoGpuSettings");
        boolean automaticSettings = "t".equals(savedAutoSetting) ||
            (savedAutoSetting.isEmpty() && preferences.getBoolean("auto_gpu_settings", true));
        automaticGpuSettingsEnabled = automaticSettings;
        if (!automaticSettings) return;

        String renderer = "";
        String vendorName = "";
        try {
            renderer = GPUHelper.glGetRenderer(this);
            vendorName = GPUHelper.glGetVendor(this);
        }
        catch (LinkageError | RuntimeException error) {
            android.util.Log.w("WinMali-GPU", "Unable to identify the GPU for launch compatibility", error);
        }

        activeGpuVendor =
            GpuCompatibilityProfile.resolve(renderer, vendorName, 0, null).vendor;
        android.util.Log.i("WinMali-GPU", "Detected renderer=" + renderer +
            ", vendor=" + vendorName + ", compatibility profile=" + activeGpuVendor);
        if (activeGpuVendor != GpuCompatibilityProfile.Vendor.MALI) return;

        String safeDXWrapper = GpuCompatibilityProfile.resolveDXWrapperForLaunch(
            dxwrapper, activeGpuVendor, true);
        if (!safeDXWrapper.equals(dxwrapper)) {
            android.util.Log.w("WinMali-GPU",
                "Mali GPU detected; replacing automatic DXVK with WineD3D for this session");
            dxwrapper = safeDXWrapper;
            dxwrapperConfig = new KeyValueSet[]{new KeyValueSet(), new KeyValueSet()};
        }
        if (dxwrapper.equals(DXWrappers.WINED3D)) {
            dxwrapperConfig[0] = GpuCompatibilityProfile.applyMaliWineD3DDefaults(
                dxwrapperConfig[0], activeGpuVendor);
            android.util.Log.i("WinMali-GPU",
                "Using Mali WineD3D Vulkan renderer with Backbuffer offscreen rendering");
        }

        String safeVulkanDriver = GpuCompatibilityProfile.resolveVulkanDriverForLaunch(
            graphicsDriver[0], activeGpuVendor, true);
        if (!safeVulkanDriver.equals(graphicsDriver[0])) {
            android.util.Log.w("WinMali-GPU",
                "Mali GPU detected; replacing automatic Turnip with Vortek for this session");
            graphicsDriver[0] = safeVulkanDriver;
            graphicsDriverConfig[0] = new KeyValueSet();
        }
    }

    private boolean setupWineSystemFiles() {
        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String rfsVersion = String.valueOf(rootFS.getVersion());
        boolean containerDataChanged = false;

        boolean wineprefixWasUpdated = WineUtils.isWineprefixWasUpdated(container);
        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("rfsVersion").equals(rfsVersion) || wineprefixWasUpdated) {
            // Apply patches before updating the marker. If copying fails, the next
            // launch must retry instead of treating a partially patched prefix as current.
            if (!applyGeneralPatches(container)) return false;
            container.putExtra("appVersion", appVersion);
            container.putExtra("rfsVersion", rfsVersion);
            containerDataChanged = true;
        }

        if (verifyUserRegistry()) containerDataChanged = true;
        String previousDXWrapper = container.getExtra("dxwrapper");
        if (!extractDXWrapperFiles()) return false;
        if (!previousDXWrapper.equals(container.getExtra("dxwrapper"))) containerDataChanged = true;

        String previousWinComponents = container.getExtra("wincomponents");
        if (!extractWinComponentFiles()) return false;
        if (!wincomponents.equals(previousWinComponents)) {
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container, true);

        String startupSelection = String.valueOf(container.getStartupSelection());
        if (!startupSelection.equals(container.getExtra("startupSelection")) || wineprefixWasUpdated) {
            WineUtils.changeServicesStatus(container, container.getStartupSelection());
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }

        boolean openAndroidBrowserFromWine = preferences.getBoolean("open_android_browser_from_wine", true);
        String openAndroidBrowserFromWineStr = openAndroidBrowserFromWine ? "t" : "f";
        if (!openAndroidBrowserFromWineStr.equals(container.getExtra("openAndroidBrowserFromWine")) || wineprefixWasUpdated) {
            WineUtils.changeBrowsersRegistryKey(container, openAndroidBrowserFromWine);
            container.putExtra("openAndroidBrowserFromWine", openAndroidBrowserFromWineStr);
            containerDataChanged = true;
        }

        if (containerDataChanged) container.saveData();
        return ensureFileManagerInstalled() && ensureDirect3DTestInstalled();
    }

    /**
     * The active rootfs path /home/xuser is a symlink to the selected container.
     * Extracting rootfs_patches directly into the rootfs is therefore rejected by
     * the archive extractor's symlink-parent protection. Stage the trusted archive
     * in cache, then overlay its system files into rootfs and its Wine files into
     * the real per-container directory.
     */
    private boolean applyRootfsPatchArchive(Container targetContainer, boolean includeSystemFiles) {
        if (targetContainer == null) return false;
        File containerRoot = targetContainer.getRootDir();
        if (containerRoot == null || !containerRoot.isDirectory() || FileUtils.isSymlink(containerRoot)) {
            android.util.Log.e("WinMali-FileManager", "Container root directory is unavailable or unsafe");
            return false;
        }

        File stagingDir = new File(getCacheDir(),
            "rootfs-patches-"+targetContainer.id+"-"+System.nanoTime());
        try {
            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                this, "rootfs_patches.tzst", stagingDir)) {
                android.util.Log.e("WinMali-FileManager", "Unable to stage rootfs_patches.tzst");
                return false;
            }

            if (includeSystemFiles) {
                File stagedOpt = new File(stagingDir, "opt");
                File stagedUsr = new File(stagingDir, "usr");
                File targetOpt = new File(rootFS.getRootDir(), "opt");
                File targetUsr = new File(rootFS.getRootDir(), "usr");
                if (!copyPatchDirectory(stagedOpt, targetOpt) ||
                    !copyPatchDirectory(stagedUsr, targetUsr)) {
                    android.util.Log.e("WinMali-FileManager", "Unable to apply rootfs system patches");
                    return false;
                }
            }

            File stagedWine = new File(stagingDir, "home/"+RootFS.USER+"/.wine");
            File targetWine = new File(containerRoot, ".wine");
            if (!copyPatchDirectory(stagedWine, targetWine)) {
                android.util.Log.e("WinMali-FileManager", "Unable to apply per-container Wine patches");
                return false;
            }

            File windowsDir = new File(targetWine, "drive_c/windows");
            if (!isNonEmptyFile(new File(windowsDir, "wfm.exe")) ||
                !isNonEmptyFile(new File(windowsDir, "winhandler.exe"))) {
                android.util.Log.e("WinMali-FileManager", "Required file-manager binaries are missing after patch application");
                return false;
            }
            return true;
        }
        catch (RuntimeException error) {
            android.util.Log.e("WinMali-FileManager", "Unable to apply rootfs patch archive", error);
            return false;
        }
        finally {
            if (stagingDir.exists()) FileUtils.delete(stagingDir);
        }
    }

    private static boolean copyPatchDirectory(File source, File destination) {
        if (source == null || destination == null || !source.isDirectory() ||
            FileUtils.isSymlink(source) || FileUtils.isSymlink(destination)) return false;
        return FileUtils.copy(source, destination,
            file -> FileUtils.chmod(file, 0771));
    }

    /**
     * Existing containers may already carry the current app-version marker but
     * still lack WFM because an earlier patch extraction stopped at the active
     * container symlink. Repair the Wine tree in the selected container itself.
     */
    private boolean ensureFileManagerInstalled() {
        File containerRoot = container != null ? container.getRootDir() : null;
        if (containerRoot == null || !containerRoot.isDirectory() || FileUtils.isSymlink(containerRoot)) return false;
        File windowsDir = new File(containerRoot, ".wine/drive_c/windows");
        if (isNonEmptyFile(new File(windowsDir, "wfm.exe")) &&
            isNonEmptyFile(new File(windowsDir, "winhandler.exe"))) return true;

        android.util.Log.w("WinMali-FileManager",
            "Container file manager files are missing; restoring per-container Wine patches");
        return applyRootfsPatchArchive(container, false);
    }

    private static boolean isNonEmptyFile(File file) {
        return file.isFile() && file.length() > 0;
    }

    /**
     * Installs the source-built Direct3D 9 diagnostic into the shared rootfs
     * on each container start. This updates existing containers without
     * rebuilding their Wine prefixes or overwriting saved game data.
     */
    private boolean ensureDirect3DTestInstalled() {
        File appsDir = new File(rootFS.getRootDir(), "opt/apps");
        if (FileUtils.isSymlink(appsDir) ||
            (!appsDir.isDirectory() && !appsDir.mkdirs())) {
            android.util.Log.e("WinMali-D3D", "Unable to prepare the shared Direct3D test directory");
            return false;
        }

        File testExecutable = new File(appsDir, "WinMaliD3DTest.exe");
        if (!FileUtils.copy(this, "testd3d/WinMaliD3DTest.exe", testExecutable) ||
            !isNonEmptyFile(testExecutable)) {
            android.util.Log.e("WinMali-D3D", "Unable to install the bundled Direct3D 9 test executable");
            return false;
        }

        // Do not leave the legacy upstream test in the Start Menu's executable
        // directory. The replacement is built from this repository's source.
        File legacyTestExecutable = new File(appsDir, "TestD3D.exe");
        if (legacyTestExecutable.exists() && !legacyTestExecutable.delete()) {
            android.util.Log.e("WinMali-D3D", "Unable to remove the legacy TestD3D.exe from the shared app directory");
            return false;
        }

        android.util.Log.i("WinMali-D3D", "Installed source-built Direct3D 9 diagnostic (" +
            testExecutable.length() + " bytes)");
        return true;
    }

    private void setupXEnvironment() {
        String rootPath = rootFS.getRootDir().getPath();
        envVars.put("MESA_DEBUG", "silent");
        envVars.put("MESA_NO_ERROR", "1");
        envVars.put("WINEPREFIX", rootPath+RootFS.WINEPREFIX);
        envVars.put("WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER", "1");

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty() ? "+"+wineDebugChannels.replace(",", ",+") : "-all");

        FileUtils.clear(rootFS.getTmpDir());

        GuestProgramLauncherComponent guestProgramLauncherComponent = new GuestProgramLauncherComponent();

        if (container != null) {
            if (container.getHUDMode() == FrameRating.Mode.FULL.ordinal()) envVars.put("X11_WND_GPU_INFO", "1");

            String desktopName = shortcut != null || getIntent().hasExtra("exec_path") ? "nogui" : "shell";
            String guestExecutable = "wine explorer /desktop="+desktopName+","+xServer.screenInfo+" "+getWineStartCommand();
            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());
            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));
            if (gameProfileEnvVars != null) envVars.putAll(gameProfileEnvVars);
            if (automaticGpuSettingsEnabled) {
                GpuCompatibilityProfile.applyMaliEnvironmentDefaults(envVars, activeGpuVendor);
            }
            if (!envVars.has("WINEESYNC")) envVars.put("WINEESYNC", "1");

            guestProgramLauncherComponent.setBox64Preset(gameProfileBox64Preset != null
                ? gameProfileBox64Preset
                : (shortcut != null ? shortcut.getExtra("box64Preset", container.getBox64Preset()) : container.getBox64Preset()));
        }

        environment = new XEnvironment(this, rootFS);
        environment.addComponent(new SysVSharedMemoryComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(new XServerComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.XSERVER_PATH)));
        environment.addComponent(new NetworkInfoUpdateComponent());

        if (audioDriver.equals(AudioDrivers.ALSA)) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath+UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", ALSAClient.USE_SHARED_MEMORY ? "true" : "false");

            ALSAClient.Options options = ALSAClient.Options.fromKeyValueSet(audioDriverConfig);
            environment.addComponent(new ALSAServerComponent(UnixSocketConfig.create(rootPath, UnixSocketConfig.ALSA_SERVER_PATH), options));
        }
        else if (audioDriver.equals(AudioDrivers.PULSEAUDIO)) {
            PulseAudioComponent pulseAudioComponent = new PulseAudioComponent(UnixSocketConfig.create(rootPath, UnixSocketConfig.PULSE_SERVER_PATH));
            envVars.put("PULSE_SERVER", rootPath+UnixSocketConfig.PULSE_SERVER_PATH);

            if (!audioDriverConfig.isEmpty()) {
                envVars.put("PULSE_LATENCY_MSEC", audioDriverConfig.getInt("latencyMillis", AudioDriverConfigDialog.DEFAULT_LATENCY_MILLIS));
                pulseAudioComponent.setVolume(audioDriverConfig.getFloat("volume", AudioDriverConfigDialog.DEFAULT_VOLUME));
                pulseAudioComponent.setPerformanceMode(audioDriverConfig.getInt("performanceMode", AudioDriverConfigDialog.DEFAULT_PERFORMANCE_MODE));
            }
            else envVars.put("PULSE_LATENCY_MSEC", AudioDriverConfigDialog.DEFAULT_LATENCY_MILLIS);
            environment.addComponent(pulseAudioComponent);
        }

        if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK)) {
            VortekRendererComponent.Options options = VortekRendererComponent.Options.fromKeyValueSet(this, graphicsDriverConfig[0]);
            VortekRendererComponent vortekRendererComponent = new VortekRendererComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.VORTEK_SERVER_PATH), options);
            environment.addComponent(vortekRendererComponent);
        }
        if (graphicsDriver[1].equals(GraphicsDrivers.VIRGL)) {
            environment.addComponent(new VirGLRendererComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.VIRGL_SERVER_PATH)));
        }

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> {
            android.util.Log.e("WinMali-Launch", "Wine/Box64 session exited with status "+status);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) exit();
            });
        });
        guestProgramLauncherComponent.setOutputCallback((line) ->
            android.util.Log.i("WinMali-Guest", line));
        environment.addComponent(guestProgramLauncherComponent);

        if (isGenerateWineprefix()) {
            wineInfo = getIntent().getParcelableExtra("wine_info");
            if (wineInfo != null) WineInstaller.generateWineprefix(wineInfo, environment);
        }
        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars = null;
        }

        // winhandler.exe sends its initial UDP handshake immediately. Bind the
        // Android listener synchronously before starting Box64/Wine or that first
        // packet can be lost and applications will never appear.
        if (!winHandler.start()) {
            android.util.Log.e("WinMali-Launch", "Unable to start WinHandler UDP listener on port 7947");
            runOnUiThread(() -> {
                AppUtils.showToast(this, R.string.winhandler_start_failed);
                finish();
            });
            return;
        }

        environment.startEnvironmentComponents();
        envVars.clear();
        graphicsDriver = null;
        dxwrapperConfig = null;
        graphicsDriverConfig = null;
        audioDriver = null;
        audioDriverConfig = null;
        wincomponents = null;
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        final GLRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);
        renderer.setCursorColor(preferences.getInt("cursor_color", 0xffffff));
        renderer.setCursorScale(preferences.getFloat("cursor_scale", 1.0f));
        renderer.setForceWindowsFullscreen(shortcut != null && shortcut.getExtra("forceFullscreen", "0").equals("1"));

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        capturePointerOnExternalMouse = preferences.getBoolean("capture_pointer_on_external_mouse", true);
        touchpadView = new TouchpadView(this, xServer, capturePointerOnExternalMouse);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.openDrawer(GravityCompat.START);
        });
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this);
        inputControlsView.setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        if (capturePointerOnExternalMouse) {
            inputControlsView.setOnCapturedPointerListener((view, event) ->
                touchpadView.isEnabled() && touchpadView.onCapturedPointer(view, event));
        }
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        if (container != null && container.getHUDMode() != FrameRating.Mode.DISABLED.ordinal()) {
            frameRating = new FrameRating(this);
            frameRating.setMode(FrameRating.Mode.values()[container.getHUDMode()]);
            frameRating.setVisibility(View.GONE);
            rootView.addView(frameRating);
        }

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null) showInputControls(profile);
            }
        }

        if (MainActivity.DEBUG_MODE) rootView.addView(AppUtils.createDebugMsgTextView(this));
        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);
    }

    private void showInputControlsDialog() {
        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);
        dialog.setTitle(R.string.input_controls);
        dialog.setIcon(R.drawable.icon_input_controls);

        final Spinner sProfile = dialog.findViewById(R.id.SProfile);
        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- "+getString(R.string.disabled)+" --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (profile == inputControlsView.getProfile()) selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));
            sProfile.setSelection(selectedPosition);
        };
        loadProfileSpinner.run();

        final CheckBox cbRelativeMouseMovement = dialog.findViewById(R.id.CBRelativeMouseMovement);
        cbRelativeMouseMovement.setChecked(xServer.isRelativeMouseMovement());

        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);
        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());

        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {
            int position = sProfile.getSelectedItemPosition();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("edit_input_controls", true);
            intent.putExtra("selected_profile_id", position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);
            editInputControlsCallback = () -> {
                hideInputControls();
                inputControlsManager.loadProfiles(true);
                loadProfileSpinner.run();
            };
            startActivityForResult(intent, MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE);
        });

        dialog.setOnConfirmCallback(() -> {
            xServer.setRelativeMouseMovement(cbRelativeMouseMovement.isChecked());
            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            }
            else hideInputControls();
        });

        dialog.show();
    }

    private void showInputControls(ControlsProfile profile) {
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();
        inputControlsView.setProfile(profile);

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);

        touchpadView.setEnabled(!profile.isDisableMouseInput());
        updateCursorVisibility();

        inputControlsView.invalidate();
    }

    private void hideInputControls() {
        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        touchpadView.setEnabled(true);
        updateCursorVisibility();

        inputControlsView.invalidate();
    }

    private void updateCursorVisibility() {
        ControlsProfile profile = inputControlsView.getProfile();
        xServerView.getRenderer().setCursorVisible(profile == null || profile.isCursorVisible());
    }

    private boolean extractGraphicsDriverFiles() {
        envVars.put("vblank_mode", "0");

        String cacheId = "";
        if (graphicsDriver[0].equals(GraphicsDrivers.TURNIP)) {
            cacheId += graphicsDriver[0]+"-"+graphicsDriverConfig[0].get("version", DefaultVersion.TURNIP);
        }
        else cacheId += graphicsDriver[0]+"-"+DefaultVersion.valueOf(graphicsDriver[0]);
        cacheId += "-"+graphicsDriver[1]+"-"+DefaultVersion.valueOf(graphicsDriver[1]);

        String currentGraphicsDriver = preferences.getString("current_graphics_driver", "");
        boolean changed = !cacheId.equals(currentGraphicsDriver);
        File rootDir = rootFS.getRootDir();
        File libDir = rootFS.getLibDir();
        File vulkanICDDir = new File(rootDir, "usr/share/vulkan/icd.d");
        File[] requiredFiles = getRequiredGraphicsDriverFiles(rootDir, libDir);
        if (requiredFiles == null || requiredFiles.length == 0) {
            android.util.Log.e("WinMali-GPU", "Unsupported graphics-driver selection: "+
                graphicsDriver[0]+","+graphicsDriver[1]);
            return false;
        }

        boolean needsInstall = GraphicsDriverInstallState.needsInstall(
            currentGraphicsDriver, cacheId, requiredFiles);
        boolean shouldExtract = needsInstall || MainActivity.DEBUG_MODE;

        if (changed) {
            FileUtils.delete(new File(libDir, "libvulkan_freedreno.so"));
            FileUtils.delete(new File(libDir, "libvulkan_vortek.so"));
            FileUtils.delete(new File(libDir, "libGL.so.1.7.0"));
            FileUtils.delete(vulkanICDDir);
            if (!vulkanICDDir.isDirectory() && !vulkanICDDir.mkdirs()) {
                android.util.Log.e("WinMali-GPU", "Unable to create Vulkan ICD directory");
                return false;
            }
        }

        if (graphicsDriver[0].equals(GraphicsDrivers.TURNIP)) {
            TurnipConfigDialog.setEnvVars(this, graphicsDriverConfig[0], envVars);
            if (shouldExtract) {
                String version = graphicsDriverConfig[0].get("version", DefaultVersion.TURNIP);
                String installedVersion = GeneralComponents.extractFileResolvedVersion(
                    GeneralComponents.Type.TURNIP, this, version, DefaultVersion.TURNIP, null);
                if (installedVersion == null) {
                    android.util.Log.e("WinMali-GPU", "Unable to install the selected Turnip driver");
                    return false;
                }
            }
        }
        else if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK) && shouldExtract) {
            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                "graphics_driver/vortek-" + DefaultVersion.VORTEK + ".tzst", rootDir)) {
                android.util.Log.e("WinMali-GPU", "Unable to install the Vortek Vulkan driver");
                return false;
            }
        }

        switch (graphicsDriver[1]) {
            case GraphicsDrivers.ZINK:
                envVars.put("GALLIUM_DRIVER", "zink");
                envVars.put("ZINK_CONTEXT_THREADED", "1");
                if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK)) envVars.put("MESA_GL_VERSION_OVERRIDE", "3.3");

                if (shouldExtract && !TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    this, "graphics_driver/zink-"+DefaultVersion.ZINK+".tzst", rootDir)) {
                    android.util.Log.e("WinMali-GPU", "Unable to install the Zink OpenGL driver");
                    return false;
                }
                break;
            case GraphicsDrivers.VIRGL:
                envVars.put("GALLIUM_DRIVER", "virpipe");
                envVars.put("VIRGL_NO_READBACK", "true");
                envVars.put("VIRGL_SERVER_PATH", rootDir+UnixSocketConfig.VIRGL_SERVER_PATH);
                VirGLConfigDialog.setEnvVars(graphicsDriverConfig[1], envVars);

                if (shouldExtract && !TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    this, "graphics_driver/virgl-"+DefaultVersion.VIRGL+".tzst", rootDir)) {
                    android.util.Log.e("WinMali-GPU", "Unable to install the VirGL OpenGL driver");
                    return false;
                }
                break;
            case GraphicsDrivers.GLADIO:
                envVars.put("GLADIO_NO_ERROR", "1");

                if (shouldExtract && !TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    this, "graphics_driver/gladio-"+DefaultVersion.GLADIO+".tzst", rootDir)) {
                    android.util.Log.e("WinMali-GPU", "Unable to install the Gladio OpenGL driver");
                    return false;
                }
                break;
            default:
                android.util.Log.e("WinMali-GPU", "Unsupported OpenGL driver: "+graphicsDriver[1]);
                return false;
        }

        if (GraphicsDriverInstallState.needsInstall(cacheId, cacheId, requiredFiles)) {
            android.util.Log.e("WinMali-GPU", "Graphics-driver files are still missing after extraction");
            return false;
        }

        // Persist the installed-driver cache key only after every required file
        // has been extracted and validated. A failed or interrupted extraction
        // therefore retries on the next container launch.
        preferences.edit().putString("current_graphics_driver", cacheId).apply();
        return true;
    }

    private File[] getRequiredGraphicsDriverFiles(File rootDir, File libDir) {
        ArrayList<File> requiredFiles = new ArrayList<>();
        switch (graphicsDriver[0]) {
            case GraphicsDrivers.VORTEK:
                requiredFiles.add(new File(libDir, "libvulkan_vortek.so"));
                requiredFiles.add(new File(rootDir, "usr/share/vulkan/icd.d/vortek_icd.aarch64.json"));
                break;
            case GraphicsDrivers.TURNIP:
                requiredFiles.add(new File(libDir, "libvulkan_freedreno.so"));
                requiredFiles.add(new File(rootDir, "usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"));
                break;
            default:
                return null;
        }

        switch (graphicsDriver[1]) {
            case GraphicsDrivers.ZINK:
            case GraphicsDrivers.VIRGL:
            case GraphicsDrivers.GLADIO:
                requiredFiles.add(new File(libDir, "libGL.so.1.7.0"));
                break;
            default:
                return null;
        }
        return requiredFiles.toArray(new File[0]);
    }

    private void showTouchpadHelpDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.touchpad_help_dialog);
        dialog.setTitle(R.string.touchpad_help);
        dialog.setIcon(R.drawable.icon_help);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return !winHandler.onGenericMotionEvent(event) && !touchpadView.onExternalMouseEvent(event) && super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||
               (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }

    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    private boolean extractDXWrapperFiles() {
        String versionKey;
        if (dxwrapper.equals(DXWrappers.DXVK)) {
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig[0], envVars);
            versionKey = dxwrapperConfig[0].get("version", DefaultVersion.DXVK(graphicsDriver[0]));
        }
        else if (dxwrapper.equals(DXWrappers.WINED3D)) {
            WineD3DConfigDialog.setEnvVars(dxwrapperConfig[0], envVars);
            versionKey = dxwrapperConfig[0].get("version", DefaultVersion.WINED3D);
        }
        else {
            android.util.Log.e("WinMali-D3D", "Unsupported Direct3D wrapper: "+dxwrapper);
            return false;
        }

        String ddrawWrapper = dxwrapperConfig[0].get("ddrawWrapper", DXWrappers.WINED3D);
        String vkd3dVersion = dxwrapperConfig[1].get("version", DefaultVersion.VKD3D);
        String cacheId = dxwrapper+"-"+versionKey+"-"+DXWrappers.VKD3D+"-"+vkd3dVersion+"-"+ddrawWrapper;
        VKD3DConfigDialog.setEnvVars(dxwrapperConfig[1], envVars);
        if (ddrawWrapper.equals(DXWrappers.CNC_DDRAW)) {
            envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\ProgramData\\cnc-ddraw\\ddraw.ini");
        }

        File rootDir = rootFS.getRootDir();
        File windowsDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");
        File[] requiredFiles = DXWrapperInstallState.getRequiredDllFiles(windowsDir, dxwrapper, ddrawWrapper);
        if (requiredFiles == null || requiredFiles.length == 0) {
            android.util.Log.e("WinMali-D3D", "Unsupported DDraw wrapper: "+ddrawWrapper);
            return false;
        }

        if (!GraphicsDriverInstallState.needsInstall(
                container.getExtra("dxwrapper"), cacheId, requiredFiles)) {
            return true;
        }

        // Persist the cache key only after every selected runtime file has
        // been extracted and validated, so partial installs retry next launch.
        if (dxwrapper.equals(DXWrappers.WINED3D)) {
            if (versionKey.equals(WineInfo.MAIN_WINE_VERSION)) {
                String[] builtInD3DDlls = {
                    "d3d8.dll", "d3d9.dll", "d3d10.dll", "d3d10_1.dll",
                    "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll",
                    "dxgi.dll", "wined3d.dll"
                };
                if (!restoreBuiltinDllFiles(builtInD3DDlls)) return false;
            }
            else if (GeneralComponents.extractFileResolvedVersion(
                    GeneralComponents.Type.WINED3D, this, versionKey, DefaultVersion.WINED3D, null) == null) {
                android.util.Log.e("WinMali-D3D", "Unable to extract the selected WineD3D version");
                return false;
            }
        }
        else {
            final boolean[] hasD3D8 = {false};
            final boolean[] hasD3D10 = {false};
            final boolean[] hasD3D10_1 = {false};
            if (GeneralComponents.extractFileResolvedVersion(
                    GeneralComponents.Type.DXVK, this, versionKey, DefaultVersion.DXVK(graphicsDriver[0]),
                    (destination, size) -> {
                        String name = destination.getName();
                        if (name.equals("d3d8.dll")) hasD3D8[0] = true;
                        else if (name.equals("d3d10.dll")) hasD3D10[0] = true;
                        else if (name.equals("d3d10_1.dll")) hasD3D10_1[0] = true;
                        return destination;
                    }) == null) {
                android.util.Log.e("WinMali-D3D", "Unable to extract the selected DXVK version");
                return false;
            }

            if (!hasD3D8[0] && !TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    this, "dxwrapper/d8vk-"+DefaultVersion.D8VK+".tzst", windowsDir)) {
                android.util.Log.e("WinMali-D3D", "Unable to install the D8VK fallback");
                return false;
            }
            if (!hasD3D10[0] && !restoreBuiltinDllFiles("d3d10.dll")) return false;
            if (!hasD3D10_1[0] && !restoreBuiltinDllFiles("d3d10_1.dll")) return false;
        }

        if (GeneralComponents.extractFileResolvedVersion(
                GeneralComponents.Type.VKD3D, this, vkd3dVersion, DefaultVersion.VKD3D, null) == null) {
            android.util.Log.e("WinMali-D3D", "Unable to extract the selected VKD3D version");
            return false;
        }

        File syswow64 = new File(windowsDir, "syswow64");
        File ddrawBackup = new File(syswow64, "ddraw_.dll");
        if (ddrawBackup.exists() && !FileUtils.delete(ddrawBackup)) return false;

        switch (ddrawWrapper) {
            case DXWrappers.CNC_DDRAW: {
                if (!restoreBuiltinDllFiles("ddraw.dll")) return false;
                String assetDir = "dxwrapper/cnc-ddraw-"+DefaultVersion.CNC_DDRAW;
                File driveC = windowsDir.getParentFile();
                File programData = new File(driveC, "ProgramData/cnc-ddraw");
                if (!FileUtils.copy(this, assetDir+"/ddraw.ini", new File(programData, "ddraw.ini"))) return false;
                File shadersDir = new File(programData, "Shaders");
                if (shadersDir.exists() && !FileUtils.delete(shadersDir)) return false;
                if (!FileUtils.copy(this, assetDir+"/Shaders", shadersDir)) return false;
                if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    this, assetDir+"/ddraw.tzst", windowsDir)) return false;
                break;
            }
            case DXWrappers.D7VK: {
                if (!restoreBuiltinDllFiles("ddraw.dll")) return false;
                File originalDdraw = new File(syswow64, "ddraw.dll");
                if (!originalDdraw.isFile() || !originalDdraw.renameTo(ddrawBackup)) {
                    android.util.Log.e("WinMali-D3D", "Unable to preserve the original 32-bit DDraw DLL");
                    return false;
                }
                if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    this, "dxwrapper/d7vk-"+DefaultVersion.D7VK+".tzst", windowsDir)) return false;
                break;
            }
            default:
                if (!restoreBuiltinDllFiles("ddraw.dll")) return false;
                break;
        }

        if (GraphicsDriverInstallState.needsInstall(cacheId, cacheId, requiredFiles)) {
            android.util.Log.e("WinMali-D3D", "Direct3D installation incomplete; cache key not updated");
            return false;
        }
        container.putExtra("dxwrapper", cacheId);
        android.util.Log.i("WinMali-D3D", "Verified wrapper install: "+cacheId);
        return true;
    }

    private boolean extractWinComponentFiles() {
        File rootDir = rootFS.getRootDir();
        File windowsDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");
        File systemRegFile = new File(rootDir, RootFS.WINEPREFIX+"/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(this, "wincomponents/wincomponents.json"));
            JSONArray direct3DNames = wincomponentsJSONObject.getJSONObject("direct3d").getJSONArray("dlnames");
            String[] direct3DDllNames = new String[direct3DNames.length()];
            for (int i = 0; i < direct3DNames.length(); i++) {
                direct3DDllNames[i] = direct3DNames.getString(i);
            }
            boolean direct3DUsesNative = "1".equals(new KeyValueSet(wincomponents).get("direct3d", "1"));
            String[] direct3DRequiredDllNames = direct3DUsesNative ?
                direct3DDllNames : new String[]{"d3dx9_43"};
            boolean direct3DFilesMissing = !WinComponentInstallState.arePresent(
                windowsDir, direct3DRequiredDllNames);

            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();
            ArrayList<String> builtinDlls = new ArrayList<>();
            ArrayList<String> direct3DBuiltinDlls = new ArrayList<>();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                String identifier = wincomponent[0];
                String oldValue = oldWinComponentsIter.next()[1];
                boolean useNative = wincomponent[1].equals("1");
                boolean componentChanged = !wincomponent[1].equals(oldValue);
                boolean repairDirect3D = identifier.equals("direct3d") && direct3DFilesMissing;
                if (!componentChanged && !repairDirect3D) continue;

                if (useNative) {
                    boolean extracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                        this, "wincomponents/"+identifier+".tzst", windowsDir);
                    if (identifier.equals("direct3d") &&
                        (!extracted || !WinComponentInstallState.arePresent(windowsDir, direct3DDllNames))) {
                        android.util.Log.e("WinMali-D3D", "Unable to install all native Direct3D component DLLs");
                        return false;
                    }
                }
                else {
                    JSONObject wincomponentJSONObject = wincomponentsJSONObject.getJSONObject(identifier);
                    if (wincomponentJSONObject.getBoolean("restoreBuiltinDlls")) {
                        JSONArray dlnames = wincomponentJSONObject.getJSONArray("dlnames");
                        for (int i = 0; i < dlnames.length(); i++) {
                            String dlname = dlnames.getString(i);
                            String dllName = !dlname.endsWith(".exe") ? dlname+".dll" : dlname;
                            if (identifier.equals("direct3d")) {
                                File wineDir = new File(rootDir, rootFS.getWinePath());
                                File source64 = new File(new File(wineDir, "lib/wine/x86_64-windows"), dllName);
                                File source32 = new File(new File(wineDir, "lib/wine/i386-windows"), dllName);
                                if (isNonEmptyFile(source64) && isNonEmptyFile(source32)) {
                                    direct3DBuiltinDlls.add(dllName);
                                }
                            }
                            else builtinDlls.add(dllName);
                        }
                    }
                    else {
                        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "wincomponents/"+identifier+".tzst", windowsDir, (destination, size) -> {
                                String name = destination.getName();
                                if (name.endsWith(".dll") || name.endsWith(".manifest") || name.endsWith("_deadbeef")) FileUtils.delete(destination);
                                return null;
                            });
                    }
                }

                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative);
            }

            if (!builtinDlls.isEmpty()) restoreBuiltinDllFiles(builtinDlls.toArray(new String[0]));
            if (!direct3DBuiltinDlls.isEmpty() &&
                !restoreBuiltinDllFiles(direct3DBuiltinDlls.toArray(new String[0]))) {
                android.util.Log.e("WinMali-D3D", "Unable to restore built-in Direct3D component DLLs");
                return false;
            }
            if (!WinComponentInstallState.arePresent(windowsDir, direct3DRequiredDllNames)) {
                android.util.Log.e("WinMali-D3D", "Direct3D component is incomplete; required DLLs are missing");
                return false;
            }

            WineUtils.overrideWinComponentDlls(this, container, wincomponents);
            return true;
        }
        catch (JSONException | RuntimeException error) {
            android.util.Log.e("WinMali-D3D", "Unable to validate or install Direct3D components", error);
            return false;
        }
    }

    private boolean restoreBuiltinDllFiles(final String... dlls) {
        File rootDir = rootFS.getRootDir();
        File wineDir = new File(rootDir, rootFS.getWinePath());
        File wineSystem32Dir = new File(wineDir, "lib/wine/x86_64-windows");
        File wineSysWoW64Dir = new File(wineDir, "lib/wine/i386-windows");
        File containerSystem32Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/system32");
        File containerSysWoW64Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/syswow64");

        for (String dll : dlls) {
            File source32 = new File(wineSysWoW64Dir, dll);
            File source64 = new File(wineSystem32Dir, dll);
            File destination32 = new File(containerSysWoW64Dir, dll);
            File destination64 = new File(containerSystem32Dir, dll);
            if (!FileUtils.copy(source32, destination32) ||
                !FileUtils.copy(source64, destination64) ||
                !isNonEmptyFile(destination32) || !isNonEmptyFile(destination64)) {
                android.util.Log.e("WinMali-D3D", "Failed to restore "+dll+" for both architectures");
                return false;
            }
        }
        return true;
    }

    private boolean isGenerateWineprefix() {
        return getIntent().getBooleanExtra("generate_wineprefix", false);
    }

    private String getWineStartCommand() {
        String cmdArgs = "";
        String execPath = null;
        String execArgs = "";

        if (shortcut != null) {
            execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " "+execArgs : "";

            if (shortcut.isLinkPath()) {
                cmdArgs = "\""+shortcut.path+"\""+execArgs;
            }
            else execPath = shortcut.path;
        }
        else {
            Intent intent = getIntent();
            if (intent.hasExtra("exec_path")) {
                execPath = WineUtils.unixToDOSPath(intent.getStringExtra("exec_path"), container);

                if (execPath.endsWith(".lnk")) {
                    cmdArgs = "\""+execPath+"\"";
                    execPath = null;
                }
            }
        }

        if (execPath != null) {
            String execDir = FileUtils.getDirname(execPath);
            String filename = FileUtils.getName(execPath);
            int dotIndex, spaceIndex;
            if ((dotIndex = filename.lastIndexOf(".")) != -1 && (spaceIndex = filename.indexOf(" ", dotIndex)) != -1) {
                execArgs = filename.substring(spaceIndex+1)+execArgs;
                filename = filename.substring(0, spaceIndex);
            }
            cmdArgs = "/dir "+StringUtils.escapeDOSPath(execDir)+" \""+filename+"\""+execArgs;
        }

        if (cmdArgs.isEmpty()) cmdArgs = "/dir C:\\windows \"wfm.exe\"";

        if (overrideEnvVars != null && overrideEnvVars.has("EXTRA_EXEC_ARGS")) {
            cmdArgs += " "+overrideEnvVars.get("EXTRA_EXEC_ARGS");
            overrideEnvVars.remove("EXTRA_EXEC_ARGS");
        }
        return "C:\\windows\\winhandler.exe "+cmdArgs;
    }

    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public RootFS getRootFs() {
        return rootFS;
    }

    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) overrideEnvVars = new EnvVars();
        return overrideEnvVars;
    }

    public String getDXWrapper() {
        return dxwrapper;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public ScreenInfo getScreenInfo() {
        return screenInfo;
    }

    public void setScreenInfo(ScreenInfo screenInfo) {
        this.screenInfo = screenInfo;
    }

    private ScreenInfo resolveScreenInfo(String value) {
        if (!value.equals("native")) return new ScreenInfo(value);

        int width = AppUtils.getScreenWidth();
        int height = AppUtils.getScreenHeight();
        return new ScreenInfo(width - (width % 2), height - (height % 2));
    }

    public String getWinComponents() {
        return wincomponents;
    }

    public void setWinComponents(String wincomponents) {
        this.wincomponents = wincomponents;
    }

    public DebugDialog getDebugDialog() {
        return debugDialog;
    }

    public String getScreenEffectProfile() {
        return screenEffectProfile;
    }

    public void setScreenEffectProfile(String screenEffectProfile) {
        this.screenEffectProfile = screenEffectProfile;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = rootFS.getRootDir();
            File userRegFile = new File(rootDir, RootFS.WINEPREFIX+"/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals(AudioDrivers.ALSA)) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                }
                else if (audioDriver.equals(AudioDrivers.PULSEAUDIO)) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private boolean applyGeneralPatches(Container container) {
        File rootDir = rootFS.getRootDir();
        FileUtils.delete(new File(rootDir, "opt/apps"));
        if (!applyRootfsPatchArchive(container, true)) return false;

        // Keep the existing separate PulseAudio payload and extraction location.
        if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
            "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"))) {
            // Preserve the pre-existing non-fatal PulseAudio fallback behaviour.
            android.util.Log.w("WinMali-FileManager", "Unable to refresh PulseAudio files");
        }

        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("dxwrapper", null);
        container.putExtra("desktopTheme", null);
        SettingsFragment.resetPreferenceVersions(this);
        return true;
    }

    public void changeFrameRatingVisibility(Window window, boolean visible) {
        if (frameRating == null) return;
        if (visible) {
            if (window.id == frameRatingWindowId) return;
            Window child = window.getChildAt(0);
            boolean viewable = window.attributes.isMapped() && window.getWidth() >= ScreenInfo.MIN_WIDTH && window.getHeight() >= ScreenInfo.MIN_HEIGHT;
            Window frameRatingWindow = null;
            if (viewable && (window.isSurface() || (child != null && child.isSurface()))) {
                frameRatingWindow = window.isSurface() ? window : child;
            }
            else if (window.isSurface() && !window.isApplicationWindow()) {
                Window parent = window.getParent();
                if (parent != null && parent.isApplicationWindow() && !parent.isSurface()) frameRatingWindow = window;
            }

            if (frameRatingWindow != null) {
                if (frameRating.getMode() == FrameRating.Mode.FULL) {
                    Property gpuInfo = frameRatingWindow.getProperty(Atom._NET_WM_GPU_INFO);
                    frameRating.setGPUInfo(gpuInfo != null ? new String(gpuInfo.data.array()) : "N/A");
                }
                frameRatingWindowId = frameRatingWindow.id;
                frameRating.reset();
            }
        }
        else if (window.id == frameRatingWindowId) {
            frameRatingWindowId = -1;
            runOnUiThread(() -> frameRating.setVisibility(View.GONE));
        }
    }

    public boolean verifyUserRegistry() {
        File userRegFile = new File(rootFS.getRootDir(), RootFS.WINEPREFIX+"/user.reg");
        String lastModified = String.valueOf(userRegFile.lastModified());

        if (!lastModified.equals(container.getExtra("userRegLastModified"))) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                registryEditor.removeKey("Software\\Wow6432Node\\Wine", true);
            }

            container.putExtra("userRegLastModified", lastModified);
            return true;
        }
        else return false;
    }
}
