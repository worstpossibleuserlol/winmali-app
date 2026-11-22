package com.winlator.core;

/** Safe parsers for values stored in Windows registry files. */
final class RegistryValueParser {
    private RegistryValueParser() {}

    static Integer parseDword(String value, Integer fallback) {
        if (value == null || value.length() <= 6 ||
            !value.regionMatches(true, 0, "dword:", 0, 6)) return fallback;

        String hex = value.substring(6);
        if (hex.isEmpty() || hex.length() > 8 || !hex.matches("[0-9a-fA-F]+")) return fallback;
        try {
            return (int)Long.parseLong(hex, 16);
        }
        catch (NumberFormatException error) {
            return fallback;
        }
    }
}
