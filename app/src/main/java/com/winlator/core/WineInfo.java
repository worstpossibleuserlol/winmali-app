package com.winlator.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WineInfo implements Parcelable {
    public static final String MAIN_WINE_VERSION = "10.10";
    public static final WineInfo MAIN_WINE_INFO = new WineInfo(MAIN_WINE_VERSION);
    private static final Pattern VERSION_OUTPUT_PATTERN = Pattern.compile(
        "^wine\\-([0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9][A-Za-z0-9.-]*)?)",
        Pattern.CASE_INSENSITIVE);
    public final String version;
    public final String subversion;
    public final String path;
    private final boolean mainRuntime;

    public WineInfo(String version) {
        this(version, null, null, true);
    }

    public WineInfo(String version, String subversion, String path) {
        this(version, subversion, path, false);
    }

    private WineInfo(String version, String subversion, String path, boolean mainRuntime) {
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.path = path;
        this.mainRuntime = mainRuntime;
    }

    private WineInfo(Parcel in) {
        version = in.readString();
        subversion = in.readString();
        path = in.readString();
        mainRuntime = in.readInt() != 0;
    }

    public String identifier() {
        return "wine-"+fullVersion()+(mainRuntime ? "-custom" : "");
    }

    public String fullVersion() {
        return version+(subversion != null ? "-"+subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        String result = "Wine "+fullVersion()+(mainRuntime ? " (Custom)" : "");
        if (path != null) {
            String architectures = CompatibilityRuntimeManifest.getArchitectureSummary(new File(path));
            if (!architectures.equals("guest ABI not detected")) result += " ["+architectures+"]";
        }
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(path);
        dest.writeInt(mainRuntime ? 1 : 0);
    }

    static String parseVersionIdentifier(String identifier) {
        if (identifier == null || !identifier.startsWith("wine-")) return null;
        String version = identifier.substring("wine-".length());
        return CompatibilityRuntimePackage.isValidVersionName(version) ? version : null;
    }

    static String parseVersionOutput(String outputLine) {
        if (outputLine == null) return null;
        Matcher matcher = VERSION_OUTPUT_PATTERN.matcher(outputLine.trim());
        if (!matcher.find()) return null;
        String version = matcher.group(1);
        return CompatibilityRuntimePackage.isValidVersionName(version) ? version : null;
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, String identifier) {
        if (identifier == null || identifier.equals(MAIN_WINE_INFO.identifier()) || context == null) {
            return MAIN_WINE_INFO;
        }
        String version = parseVersionIdentifier(identifier);
        if (version == null) return MAIN_WINE_INFO;

        File installedWineDir = RootFS.find(context).getInstalledWineDir();
        String path = (new File(installedWineDir, identifier)).getPath();
        return new WineInfo(version, null, path);
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null ||wineVersion.equals(MAIN_WINE_INFO.identifier());
    }
}
