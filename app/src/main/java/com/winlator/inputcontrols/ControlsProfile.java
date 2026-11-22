package com.winlator.inputcontrols;

import android.content.Context;

import androidx.annotation.NonNull;

import com.winlator.core.FileUtils;
import com.winlator.widget.InputControlsView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ControlsProfile implements Comparable<ControlsProfile>, GamepadSlot {
    public final int id;
    private String name;
    private float cursorSpeed = 1.0f;
    private boolean disableMouseInput = false;
    private boolean hideMouseCursor;
    private final ArrayList<ControlElement> elements = new ArrayList<>();
    private final ArrayList<ExternalController> controllers = new ArrayList<>();
    private final List<ControlElement> immutableElements = Collections.unmodifiableList(elements);
    private boolean elementsLoaded = false;
    private boolean controllersLoaded = false;
    private boolean virtualGamepad = false;
    private final Context context;
    private GamepadState gamepadState;
    private GamepadVibration gamepadVibration;

    public ControlsProfile(Context context, int id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public short getVendorId() {
        return 0x0001;
    }

    @Override
    public short getProductId() {
        return 0x0001;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getCursorSpeed() {
        return cursorSpeed;
    }

    public void setCursorSpeed(float cursorSpeed) {
        this.cursorSpeed = cursorSpeed;
    }

    public boolean isDisableMouseInput() {
        return disableMouseInput;
    }

    public void setDisableMouseInput(boolean disableMouseInput) {
        this.disableMouseInput = disableMouseInput;
    }

    public boolean isHideMouseCursor() {
        return hideMouseCursor;
    }

    public void setHideMouseCursor(boolean hideMouseCursor) {
        this.hideMouseCursor = hideMouseCursor;
    }

    public boolean isCursorVisible() {
        return !disableMouseInput && !hideMouseCursor;
    }

    public boolean isVirtualGamepad() {
        return virtualGamepad;
    }

    @Override
    public GamepadState getGamepadState() {
        if (gamepadState == null) gamepadState = new GamepadState();
        return gamepadState;
    }

    @Override
    public GamepadVibration getGamepadVibration() {
        if (gamepadVibration == null) gamepadVibration = new GamepadVibration(context);
        return gamepadVibration;
    }

    public ExternalController addController(String id) {
        if (id == null || id.isEmpty()) return null;
        ExternalController controller = getController(id);
        if (controller == null) controller = ExternalController.getController(id);
        if (controller != null && !controllers.contains(controller)) controllers.add(controller);
        controllersLoaded = true;
        return controller;
    }

    public void removeController(ExternalController controller) {
        if (!controllersLoaded) loadControllers();
        controllers.remove(controller);
    }

    public ExternalController getController(String id) {
        if (!controllersLoaded) loadControllers();
        if (id == null) return null;
        for (ExternalController controller : controllers) {
            if (controller != null && id.equals(controller.getId())) return controller;
        }
        return null;
    }

    public ExternalController getController(int deviceId) {
        if (!controllersLoaded) loadControllers();
        for (ExternalController controller : controllers) if (controller.getDeviceId() == deviceId) return controller;
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(ControlsProfile o) {
        return Integer.compare(id, o.id);
    }

    public boolean isElementsLoaded() {
        return elementsLoaded;
    }

    public boolean save() {
        if (context == null || id < 0 || name == null || name.trim().isEmpty() ||
            !Float.isFinite(cursorSpeed)) return false;
        File file = getProfileFile(context, id);

        try {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("name", name);
            data.put("cursorSpeed", Float.valueOf(cursorSpeed));
            if (disableMouseInput) data.put("disableMouseInput", disableMouseInput);
            if (hideMouseCursor) data.put("hideMouseCursor", true);

            JSONArray elementsJSONArray = new JSONArray();
            if (!elementsLoaded && file.isFile()) {
                JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
                elementsJSONArray = profileJSONObject.getJSONArray("elements");
            }
            else for (ControlElement element : elements) elementsJSONArray.put(element.toJSONObject());
            data.put("elements", elementsJSONArray);

            JSONArray controllersJSONArray = new JSONArray();
            if (!controllersLoaded && file.isFile()) {
                JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
                if (profileJSONObject.has("controllers")) controllersJSONArray = profileJSONObject.getJSONArray("controllers");
            }
            else {
                for (ExternalController controller : controllers) {
                    JSONObject controllerJSONObject = controller.toJSONObject();
                    if (controllerJSONObject != null) controllersJSONArray.put(controllerJSONObject);
                }
            }
            if (controllersJSONArray.length() > 0) data.put("controllers", controllersJSONArray);

            return FileUtils.writeString(file, data.toString());
        }
        catch (JSONException | RuntimeException error) {
            android.util.Log.w("WinMali-Controls", "Unable to save control profile", error);
            return false;
        }
    }

    public static File getProfileFile(Context context, int id) {
        return new File(InputControlsManager.getProfilesDir(context), "controls-"+id+".icp");
    }

    public void addElement(ControlElement element) {
        elements.add(element);
        elementsLoaded = true;
    }

    public void removeElement(ControlElement element) {
        elements.remove(element);
        elementsLoaded = true;
    }

    public List<ControlElement> getElements() {
        return immutableElements;
    }

    public boolean isTemplate() {
        return name != null && name.toLowerCase(Locale.ENGLISH).contains("template");
    }

    public ArrayList<ExternalController> loadControllers() {
        controllers.clear();
        controllersLoaded = true;

        File file = getProfileFile(context, id);
        if (!file.isFile() || FileUtils.isSymlink(file)) return controllers;

        ArrayList<ExternalController> loadedControllers = new ArrayList<>();
        try {
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
            if (!profileJSONObject.has("controllers")) return controllers;
            JSONArray controllersJSONArray = profileJSONObject.getJSONArray("controllers");
            for (int i = 0; i < controllersJSONArray.length(); i++) {
                JSONObject controllerJSONObject = controllersJSONArray.getJSONObject(i);
                String controllerId = controllerJSONObject.getString("id");
                String controllerName = controllerJSONObject.getString("name");
                if (controllerId.isEmpty() || controllerName.trim().isEmpty()) continue;

                ExternalController controller = new ExternalController();
                controller.setId(controllerId);
                controller.setName(controllerName);

                JSONArray bindings = controllerJSONObject.getJSONArray("controllerBindings");
                for (int j = 0; j < bindings.length(); j++) {
                    JSONObject bindingJson = bindings.getJSONObject(j);
                    ExternalControllerBinding binding = new ExternalControllerBinding();
                    binding.setKeyCode(bindingJson.getInt("keyCode"));
                    binding.setBinding(Binding.fromString(bindingJson.getString("binding")));
                    controller.addControllerBinding(binding);
                }
                loadedControllers.add(controller);
            }
        }
        catch (JSONException | RuntimeException error) {
            android.util.Log.w("WinMali-Controls", "Ignoring malformed controller profile data", error);
            loadedControllers.clear();
        }

        controllers.addAll(loadedControllers);
        return controllers;
    }

    public void loadElements(InputControlsView inputControlsView) {
        elements.clear();
        elementsLoaded = true;
        virtualGamepad = false;

        File file = getProfileFile(context, id);
        if (!file.isFile() || FileUtils.isSymlink(file)) return;

        ArrayList<ControlElement> loadedElements = new ArrayList<>();
        boolean loadedVirtualGamepad = false;
        try {
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
            JSONArray elementsJSONArray = profileJSONObject.getJSONArray("elements");
            for (int i = 0; i < elementsJSONArray.length(); i++) {
                JSONObject elementJSONObject = elementsJSONArray.getJSONObject(i);

                ControlElement element = null;
                if (inputControlsView != null) {
                    element = new ControlElement(inputControlsView);
                    element.setType(ControlElement.Type.valueOf(elementJSONObject.getString("type")));
                    element.setShape(ControlElement.Shape.valueOf(elementJSONObject.getString("shape")));
                    element.setToggleSwitch(elementJSONObject.getBoolean("toggleSwitch"));
                    element.setX((int)(elementJSONObject.getDouble("x") * inputControlsView.getMaxWidth()));
                    element.setY((int)(elementJSONObject.getDouble("y") * inputControlsView.getMaxHeight()));
                    element.setScale((float)elementJSONObject.getDouble("scale"));
                    element.setText(elementJSONObject.getString("text"));
                    element.setIconId(elementJSONObject.getInt("iconId"));
                    if (elementJSONObject.has("range")) element.setRange(ControlElement.Range.valueOf(elementJSONObject.getString("range")));
                    if (elementJSONObject.has("orientation")) element.setOrientation((byte)elementJSONObject.getInt("orientation"));
                    if (elementJSONObject.has("mouseMoveMode")) element.setMouseMoveMode(true);
                    if (elementJSONObject.has("opacity")) element.setOpacity((float)elementJSONObject.getDouble("opacity"));
                }

                boolean hasGamepadBinding = true;
                JSONArray bindingsJSONArray = elementJSONObject.getJSONArray("bindings");
                if (bindingsJSONArray.length() > 64) {
                    throw new IllegalArgumentException("Control profile contains too many bindings");
                }
                for (int j = 0; j < bindingsJSONArray.length(); j++) {
                    Binding binding = Binding.fromString(bindingsJSONArray.getString(j));
                    if (element != null) element.setBindingAt(j, binding);
                    if (!binding.isGamepad()) hasGamepadBinding = false;
                }

                if (!loadedVirtualGamepad && hasGamepadBinding) loadedVirtualGamepad = true;
                if (element != null) loadedElements.add(element);
            }
            elements.addAll(loadedElements);
            virtualGamepad = loadedVirtualGamepad;
        }
        catch (JSONException | RuntimeException error) {
            // Treat malformed imported bindings as invalid profile data. The overlay
            // should not crash or retry parsing the same bad file on every draw.
            android.util.Log.w("WinMali-Controls", "Ignoring malformed touchscreen profile data", error);
            elements.clear();
            virtualGamepad = false;
        }
    }
}
