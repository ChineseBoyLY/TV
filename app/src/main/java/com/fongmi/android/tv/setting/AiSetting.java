package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;

/** Settings for the optional OpenAI-compatible AI gateway. */
public final class AiSetting {
    private AiSetting() {}

    public static String getBaseUrl() { return Prefers.getString("ai_base_url", "https://api.openai.com/v1"); }
    public static void putBaseUrl(String value) { Prefers.put("ai_base_url", trim(value)); }
    public static String getApiKey() { return Prefers.getString("ai_api_key", ""); }
    public static void putApiKey(String value) { Prefers.put("ai_api_key", value == null ? "" : value.trim()); }
    public static String getModel() { return Prefers.getString("ai_model", "gpt-4o-mini"); }
    public static void putModel(String value) { Prefers.put("ai_model", trim(value)); }
    public static boolean isSubtitleEnabled() { return Prefers.getBoolean("ai_subtitle_enabled", false); }
    public static void putSubtitleEnabled(boolean enabled) { Prefers.put("ai_subtitle_enabled", enabled); }
    public static boolean isConfigured() { return !TextUtils.isEmpty(getBaseUrl()) && !TextUtils.isEmpty(getApiKey()); }

    public static String endpoint(String path) {
        String base = getBaseUrl().replaceAll("/+$", "");
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
