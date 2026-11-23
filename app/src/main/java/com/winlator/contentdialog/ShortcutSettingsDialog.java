package com.winlator.contentdialog;

import android.content.Context;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import com.winlator.ContainerDetailFragment;
import com.winlator.R;
import com.winlator.ShortcutsFragment;
import com.winlator.box64.Box64PresetManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.core.AppUtils;
import com.winlator.container.DXWrapperPicker;
import com.winlator.core.EnvVars;
import com.winlator.core.GameCompatibilityProfiles;
import com.winlator.core.GpuCompatibilityProfile;
import com.winlator.container.GraphicsDriverPicker;
import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.widget.EnvVarsView;
import com.winlator.win32.MSLink;
import com.winlator.win32.PEParser;
import com.winlator.winhandler.GamepadHandler;

import java.io.File;
import java.util.ArrayList;

public class ShortcutSettingsDialog extends ContentDialog {
    private final ShortcutsFragment fragment;
    private final Shortcut shortcut;
    private InputControlsManager inputControlsManager;

    public ShortcutSettingsDialog(ShortcutsFragment fragment, Shortcut shortcut) {
        super(fragment.getContext(), R.layout.shortcut_settings_dialog);
        this.fragment = fragment;
        this.shortcut = shortcut;
        setTitle(shortcut.name);
        setIcon(R.drawable.icon_settings);

        createContentView();
    }

    private void createContentView() {
        final Context context = fragment.getContext();
        inputControlsManager = new InputControlsManager(context);
        LinearLayout llContent = findViewById(R.id.LLContent);
        llContent.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);

        final EditText etName = findViewById(R.id.ETName);
        etName.setText(shortcut.name);

        final EditText etExecArgs = findViewById(R.id.ETExecArgs);
        etExecArgs.setText(shortcut.getExtra("execArgs"));

        ContainerDetailFragment.loadScreenSizeSpinner(getContentView(), shortcut.getExtra("screenSize", shortcut.container.getScreenSize()));

        final String oldGraphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig());
        String selectedGraphicsDriver = shortcut.getExtra("graphicsDriver", shortcut.container.getGraphicsDriver());
        final GraphicsDriverPicker[] graphicsDriverPicker = new GraphicsDriverPicker[1];
        graphicsDriverPicker[0] = new GraphicsDriverPicker(findViewById(R.id.LLGraphicsDriver),
            selectedGraphicsDriver, oldGraphicsDriverConfig);

        String oldDXWrapperConfig = shortcut.getExtra("dxwrapperConfig", shortcut.container.getDXWrapperConfig());
        String selectedDXWrapper = shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper());
        final DXWrapperPicker[] dxwrapperPicker = new DXWrapperPicker[1];
        dxwrapperPicker[0] = new DXWrapperPicker(findViewById(R.id.LLDXWrapper),
            graphicsDriverPicker[0], selectedDXWrapper, oldDXWrapperConfig);

        findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        final Spinner sAudioDriver = findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, shortcut.getExtra("audioDriver", shortcut.container.getAudioDriver()));

        final View vAudioDriverConfig = findViewById(R.id.BTAudioDriverConfig);
        vAudioDriverConfig.setTag(shortcut.getExtra("audioDriverConfig", shortcut.container.getAudioDriverConfig()));
        vAudioDriverConfig.setOnClickListener((v) -> (new AudioDriverConfigDialog(v)).show());

        final CheckBox cbForceFullscreen = findViewById(R.id.CBForceFullscreen);
        cbForceFullscreen.setChecked(shortcut.getExtra("forceFullscreen", "0").equals("1"));

        final Spinner sBox64Preset = findViewById(R.id.SBox64Preset);
        Box64PresetManager.loadSpinner(sBox64Preset, shortcut.getExtra("box64Preset", shortcut.container.getBox64Preset()));

        final Spinner sControlsProfile = findViewById(R.id.SControlsProfile);
        loadControlsProfileSpinner(sControlsProfile, shortcut.getExtra("controlsProfile", "0"));

        final Spinner sDInputMapperType = findViewById(R.id.SDInputMapperType);
        int dinputMapperType = GamepadHandler.DINPUT_MAPPER_TYPE_XINPUT;
        try {
            int savedMapperType = Integer.parseInt(shortcut.getExtra("dinputMapperType",
                String.valueOf(GamepadHandler.DINPUT_MAPPER_TYPE_XINPUT)));
            if (savedMapperType >= 0 && savedMapperType < sDInputMapperType.getCount()) {
                dinputMapperType = savedMapperType;
            }
        }
        catch (NumberFormatException ignored) {}
        sDInputMapperType.setSelection(dinputMapperType);

        ContainerDetailFragment.createWinComponentsTab(getContentView(), shortcut.getExtra("wincomponents", shortcut.container.getWinComponents()));
        final EnvVarsView envVarsView = createEnvVarsTab();

        final Spinner sGameCompatibilityProfile = findViewById(R.id.SGameCompatibilityProfile);
        final TextView tvGameCompatibilityProfileInfo = findViewById(R.id.TVGameCompatibilityProfileInfo);
        final String[] profileIds = GameCompatibilityProfiles.getProfileIds();
        final ArrayList<String> profileLabels = new ArrayList<>();
        for (String profileId : profileIds) {
            profileLabels.add(getGameCompatibilityProfileLabel(context, profileId));
        }
        sGameCompatibilityProfile.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, profileLabels));

        String savedProfileId = shortcut.getExtra("gameCompatibilityProfile", GameCompatibilityProfiles.CUSTOM);
        int selectedProfilePosition = 0;
        for (int i = 0; i < profileIds.length; i++) {
            if (profileIds[i].equals(savedProfileId)) {
                selectedProfilePosition = i;
                break;
            }
        }
        sGameCompatibilityProfile.setSelection(selectedProfilePosition, false);
        sGameCompatibilityProfile.setEnabled(false);

        final GpuCompatibilityProfile[] detectedGPUProfile = new GpuCompatibilityProfile[1];
        final Handler mainHandler = new Handler(context.getMainLooper());
        GpuCompatibilityProfile.detectAsync(context, profile -> mainHandler.post(() -> {
            detectedGPUProfile[0] = profile;
            sGameCompatibilityProfile.setEnabled(true);
            tvGameCompatibilityProfileInfo.setText(context.getString(
                R.string.game_compatibility_profile_detected,
                profile.getVendorLabel(), profile.getVulkanVersionLabel()));
        }));

        final int[] lastProfilePosition = {selectedProfilePosition};
        sGameCompatibilityProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == lastProfilePosition[0]) return;
                lastProfilePosition[0] = position;

                if (position < 0 || position >= profileIds.length) return;
                String profileId = profileIds[position];
                GameCompatibilityProfiles.Preset preset =
                    GameCompatibilityProfiles.resolve(profileId, detectedGPUProfile[0]);

                if (preset == null) {
                    tvGameCompatibilityProfileInfo.setText(
                        R.string.game_compatibility_profile_custom_selected);
                    return;
                }
                if (detectedGPUProfile[0] == null) return;

                graphicsDriverPicker[0] = new GraphicsDriverPicker(
                    findViewById(R.id.LLGraphicsDriver), preset.graphicsDriver, "");
                dxwrapperPicker[0] = new DXWrapperPicker(
                    findViewById(R.id.LLDXWrapper), graphicsDriverPicker[0], preset.dxWrapper, "");
                Box64PresetManager.loadSpinner(sBox64Preset, preset.box64Preset);

                EnvVars profileEnvVars = new EnvVars(envVarsView.getEnvVars());
                profileEnvVars.putAll(new EnvVars(preset.environmentVariables));
                envVarsView.setEnvVars(profileEnvVars);

                String message = context.getString(R.string.game_compatibility_profile_description) +
                    "\n" + detectedGPUProfile[0].getSummary();
                if (preset.experimental) {
                    message += "\n" + context.getString(R.string.game_compatibility_profile_warning);
                }
                tvGameCompatibilityProfileInfo.setText(message);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AppUtils.setupTabLayout(getContentView(), R.id.TabLayout, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabAdvanced);

        findViewById(R.id.BTNameMenu).setOnClickListener((v) -> {
            File peFile = null;
            MSLink.LinkInfo linkInfo = MSLink.extractLinkInfo(shortcut.getLinkFile());
            if (linkInfo != null) peFile = new File(WineUtils.dosToUnixPath(linkInfo.targetPath, shortcut.container));
            if (peFile == null) return;

            PEParser.FileVersionInfo fileVersionInfo = PEParser.getFileVersionInfo(peFile);
            if (fileVersionInfo != null && !fileVersionInfo.FileDescription.isEmpty() &&
                                           !fileVersionInfo.OriginalFilename.isEmpty()) {
                PopupMenu popupMenu = new PopupMenu(context, v);
                Menu menu = popupMenu.getMenu();
                menu.add(fileVersionInfo.FileDescription);
                menu.add(FileUtils.getBasename(fileVersionInfo.OriginalFilename));
                popupMenu.setOnMenuItemClickListener((menuItem) -> {
                    etName.setText(String.valueOf(menuItem.getTitle()));
                    return true;
                });
                popupMenu.show();
            }
        });

        findViewById(R.id.BTExtraArgsMenu).setOnClickListener((v) -> {
            PopupMenu popupMenu = new PopupMenu(context, v);
            popupMenu.inflate(R.menu.extra_args_popup_menu);
            popupMenu.setOnMenuItemClickListener((menuItem) -> {
                String value = String.valueOf(menuItem.getTitle());
                String execArgs = etExecArgs.getText().toString();
                if (!execArgs.contains(value)) etExecArgs.setText(!execArgs.isEmpty() ? execArgs+" "+value : value);
                return true;
            });
            popupMenu.show();
        });

        setOnConfirmCallback(() -> {
            String name = etName.getText().toString().trim();
            String graphicsDriver = graphicsDriverPicker[0].getGraphicsDriver();
            String dxwrapper = dxwrapperPicker[0].getDXWrapper();
            String dxwrapperConfig = dxwrapperPicker[0].getDXWrapperConfig();
            String graphicsDriverConfig = graphicsDriverPicker[0].getGraphicsDriverConfig();
            String audioDriverConfig = vAudioDriverConfig.getTag().toString();
            String audioDriver = StringUtils.parseIdentifier(sAudioDriver.getSelectedItem());
            String screenSize = ContainerDetailFragment.getScreenSize(getContentView());

            String execArgs = etExecArgs.getText().toString();
            shortcut.putExtra("execArgs", !execArgs.isEmpty() ? execArgs : null);
            shortcut.putExtra("screenSize", !screenSize.equals(shortcut.container.getScreenSize()) ? screenSize : null);
            shortcut.putExtra("graphicsDriver", !graphicsDriver.equals(shortcut.container.getGraphicsDriver()) ? graphicsDriver : null);
            shortcut.putExtra("dxwrapper", !dxwrapper.equals(shortcut.container.getDXWrapper()) ? dxwrapper : null);
            shortcut.putExtra("dxwrapperConfig", !dxwrapperConfig.equals(shortcut.container.getDXWrapperConfig()) ? dxwrapperConfig : null);
            shortcut.putExtra("graphicsDriverConfig", !graphicsDriverConfig.equals(shortcut.container.getGraphicsDriverConfig()) ? graphicsDriverConfig : null);
            shortcut.putExtra("audioDriver", !audioDriver.equals(shortcut.container.getAudioDriver())? audioDriver : null);
            shortcut.putExtra("audioDriverConfig", !audioDriverConfig.equals(shortcut.container.getAudioDriverConfig()) ? audioDriverConfig : null);
            shortcut.putExtra("forceFullscreen", cbForceFullscreen.isChecked() ? "1" : null);

            String wincomponents = ContainerDetailFragment.getWinComponents(getContentView());
            shortcut.putExtra("wincomponents", !wincomponents.equals(shortcut.container.getWinComponents()) ? wincomponents : null);

            String envVars = envVarsView.getEnvVars();
            shortcut.putExtra("envVars", !envVars.isEmpty() ? envVars : null);

            String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
            shortcut.putExtra("box64Preset", !box64Preset.equals(shortcut.container.getBox64Preset()) ? box64Preset : null);

            int selectedProfile = sGameCompatibilityProfile.getSelectedItemPosition();
            String gameCompatibilityProfile = selectedProfile >= 0 && selectedProfile < profileIds.length ?
                profileIds[selectedProfile] : GameCompatibilityProfiles.CUSTOM;
            shortcut.putExtra("gameCompatibilityProfile",
                !GameCompatibilityProfiles.CUSTOM.equals(gameCompatibilityProfile) ? gameCompatibilityProfile : null);

            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            int controlsProfile = sControlsProfile.getSelectedItemPosition() > 0 ? profiles.get(sControlsProfile.getSelectedItemPosition()-1).id : 0;
            shortcut.putExtra("controlsProfile", controlsProfile > 0 ? String.valueOf(controlsProfile) : null);

            int dinputMapperType = sDInputMapperType.getSelectedItemPosition();
            shortcut.putExtra("dinputMapperType", dinputMapperType != GamepadHandler.DINPUT_MAPPER_TYPE_XINPUT ? String.valueOf(dinputMapperType) : null);

            if (!shortcut.saveData()) {
                AppUtils.showToast(context, R.string.unable_to_save_shortcut_settings);
                return;
            }
            if (!shortcut.name.equals(name) && !name.isEmpty()) renameShortcut(name);

            String selectedVulkanDriver = GraphicsDrivers.parseIdentifiers(graphicsDriver)[0];
            boolean requireRestart = GraphicsDrivers.VORTEK.equals(selectedVulkanDriver) &&
                VortekConfigDialog.isRequireRestart(oldGraphicsDriverConfig, graphicsDriverConfig);
            if (requireRestart) ContentDialog.confirm(context, R.string.the_settings_have_been_changed_do_you_want_to_restart_the_app, () -> AppUtils.restartApplication(context));
        });
    }

    private static String getGameCompatibilityProfileLabel(Context context, String profileId) {
        switch (profileId) {
            case GameCompatibilityProfiles.COMPATIBILITY:
                return context.getString(R.string.game_compatibility_profile_compatibility);
            case GameCompatibilityProfiles.BALANCED:
                return context.getString(R.string.game_compatibility_profile_balanced);
            case GameCompatibilityProfiles.PERFORMANCE:
                return context.getString(R.string.game_compatibility_profile_performance);
            case GameCompatibilityProfiles.EXPERIMENTAL_DXVK:
                return context.getString(R.string.game_compatibility_profile_experimental_dxvk);
            default:
                return context.getString(R.string.game_compatibility_profile_custom);
        }
    }

    private void renameShortcut(String newName) {
        newName = StringUtils.clearReservedChars(newName);
        File parent = shortcut.file.getParentFile();
        File newFile = new File(parent, newName+".desktop");
        if (!newFile.isFile()) shortcut.file.renameTo(newFile);

        File linkFile = new File(parent, shortcut.name+".lnk");
        if (linkFile.isFile()) {
            newFile = new File(parent, newName+".lnk");
            if (!newFile.isFile()) linkFile.renameTo(newFile);
        }
        fragment.refreshContent();
    }

    private EnvVarsView createEnvVarsTab() {
        final View view = getContentView();
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        envVarsView.setEnvVars(new EnvVars(shortcut.getExtra("envVars")));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        return envVarsView;
    }

    private void loadControlsProfileSpinner(Spinner spinner, String selectedValue) {
        final Context context = fragment.getContext();
        final ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        ArrayList<String> values = new ArrayList<>();
        values.add(context.getString(R.string.none));

        int selectedPosition = 0;
        int selectedId = Integer.parseInt(selectedValue);
        for (int i = 0; i < profiles.size(); i++) {
            ControlsProfile profile = profiles.get(i);
            if (profile.id == selectedId) selectedPosition = i + 1;
            values.add(profile.getName());
        }

        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(selectedPosition, false);
    }
}
