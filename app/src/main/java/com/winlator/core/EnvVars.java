package com.winlator.core;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class EnvVars implements Iterable<String> {
    private final LinkedHashMap<String, String> data = new LinkedHashMap<>();

    public EnvVars() {}

    public EnvVars(String values) {
        putAll(values);
    }

    public EnvVars put(String name, Object value) {
        if (name != null && !name.isEmpty()) data.put(name, String.valueOf(value));
        return this;
    }

    public void putAll(String[] items) {
        if (items == null) return;
        for (String item : items) {
            if (item == null) continue;
            int index = item.indexOf('=');
            if (index <= 0) continue;

            String name = item.substring(0, index).trim();
            if (!name.isEmpty()) data.put(name, item.substring(index + 1));
        }
    }

    public void putAll(String values) {
        if (values == null || values.isEmpty()) return;

        StringBuilder token = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            char current = values.charAt(i);
            if (current == '\\' && i + 1 < values.length() &&
                Character.isWhitespace(values.charAt(i + 1))) {
                token.append(values.charAt(++i));
            }
            else if (Character.isWhitespace(current)) {
                if (token.length() > 0) {
                    putAll(new String[]{token.toString()});
                    token.setLength(0);
                }
            }
            else token.append(current);
        }
        if (token.length() > 0) putAll(new String[]{token.toString()});
    }

    public void putAll(EnvVars envVars) {
        if (envVars != null) data.putAll(envVars.data);
    }

    public String get(String name) {
        return data.getOrDefault(name, "");
    }

    public void remove(String name) {
        data.remove(name);
    }

    public boolean has(String name) {
        return data.containsKey(name);
    }

    public void clear() {
        data.clear();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return String.join(" ", toStringArray());
    }

    public String toEscapedString() {
        StringBuilder result = new StringBuilder();
        for (String key : data.keySet()) {
            if (result.length() > 0) result.append(' ');
            result.append(key).append('=').append(data.get(key).replace(" ", "\\ "));
        }
        return result.toString();
    }

    public String[] toStringArray() {
        String[] stringArray = new String[data.size()];
        int index = 0;
        for (String key : data.keySet()) stringArray[index++] = key + "=" + data.get(key);
        return stringArray;
    }

    @NonNull
    @Override
    public Iterator<String> iterator() {
        return data.keySet().iterator();
    }
}
