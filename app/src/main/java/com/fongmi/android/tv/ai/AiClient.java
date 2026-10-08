package com.fongmi.android.tv.ai;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.AiSetting;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class AiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private AiClient() {}

    public static String translate(@NonNull String text) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", AiSetting.getModel());
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject(); system.addProperty("role", "system"); system.addProperty("content", "Translate the following subtitle into natural Simplified Chinese. Keep line breaks and return only the translated text.");
        JsonObject user = new JsonObject(); user.addProperty("role", "user"); user.addProperty("content", text);
        messages.add(system); messages.add(user); body.add("messages", messages); body.addProperty("temperature", 0.2);
        JsonObject response = post("/chat/completions", body);
        return response.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
    }

    /** Translate a group of cues in one request. The indexes keep timing outside the model. */
    public static List<String> translateBatch(@NonNull List<String> texts) throws IOException {
        if (texts.isEmpty()) return new ArrayList<>();
        JsonObject body = new JsonObject();
        body.addProperty("model", AiSetting.getModel());
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "你是影视字幕翻译器。把每一条字幕翻译成自然、简洁的简体中文。严格只返回 JSON 字符串数组，数组顺序和输入一致，不要 Markdown，不要解释。");
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        JsonArray input = new JsonArray();
        for (String text : texts) input.add(text);
        user.addProperty("content", input.toString());
        messages.add(system);
        messages.add(user);
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        JsonObject response = post("/chat/completions", body);
        String content = response.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString().trim();
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end <= start) throw new IOException("AI字幕返回格式错误");
        JsonArray output = com.google.gson.JsonParser.parseString(content.substring(start, end + 1)).getAsJsonArray();
        if (output.size() != texts.size()) throw new IOException("AI字幕数量不一致");
        List<String> result = new ArrayList<>();
        output.forEach(item -> result.add(item.getAsString().trim()));
        return result;
    }

    public static byte[] speech(@NonNull String text) throws IOException {
        JsonObject body = new JsonObject(); body.addProperty("model", "tts-1"); body.addProperty("voice", AiSetting.getVoice()); body.addProperty("input", text); body.addProperty("response_format", "mp3");
        Request request = new Request.Builder().url(AiSetting.endpoint("/audio/speech")).header("Authorization", "Bearer " + AiSetting.getApiKey()).post(RequestBody.create(App.gson().toJson(body), JSON)).build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("AI语音请求失败: " + response.code());
            return response.body().bytes();
        }
    }

    private static JsonObject post(String path, JsonObject body) throws IOException {
        Request request = new Request.Builder().url(AiSetting.endpoint(path)).header("Authorization", "Bearer " + AiSetting.getApiKey()).post(RequestBody.create(App.gson().toJson(body), JSON)).build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (response.body() == null) throw new IOException("AI请求失败: " + response.code());
            String content = response.body().string();
            if (!response.isSuccessful()) {
                try {
                    JsonObject error = App.gson().fromJson(content, JsonObject.class);
                    if (error.has("error")) throw new IOException("AI请求失败: " + error.get("error").toString());
                } catch (IOException error) {
                    throw error;
                } catch (Exception ignored) {
                    // Fall through to the status code when the gateway returned non-JSON.
                }
                throw new IOException("AI请求失败: HTTP " + response.code());
            }
            JsonObject result = App.gson().fromJson(content, JsonObject.class);
            if (result == null || !result.has("choices")) throw new IOException("AI返回内容无效");
            return result;
        }
    }
}
