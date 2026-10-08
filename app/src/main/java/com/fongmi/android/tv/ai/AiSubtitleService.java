package com.fongmi.android.tv.ai;

import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.AiSetting;
import com.fongmi.android.tv.ai.local.LocalAiProvider;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AiSubtitleService {
    public interface Callback { void onSuccess(Sub subtitle, List<AiSubtitleCue> cues); void onError(String message); }
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Pattern HLS_SUBTITLE = Pattern.compile("#EXT-X-MEDIA:[^\\r\\n]*TYPE=SUBTITLES[^\\r\\n]*URI=\\\"([^\\\"]+)\\\"[^\\r\\n]*", Pattern.CASE_INSENSITIVE);
    private AiSubtitleService() {}

    public static void translate(PlayerManager player, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                Map<String, String> headers = player.getHeaders();
                Sub source = player.getSubs().stream().filter(item -> item != null && !item.isEmpty()).findFirst().orElse(null);
                if (source == null) source = findEmbeddedSubtitle(player.getUrl(), headers);
                if (source == null) throw new IllegalStateException("当前视频没有可读取的字幕文件（仅支持外部 SRT/VTT 或 HLS 内嵌字幕）");
                final Sub subtitle = source;
                String raw = read(subtitle.getUrl(), headers);
                List<AiSubtitleCue> original = AiSubtitleParser.parse(raw);
                if (original.isEmpty()) throw new IllegalStateException("字幕格式不受支持，仅支持 SRT/VTT");
                LocalAiProvider local = new LocalAiProvider(App.get());
                local.translate(original, new LocalAiProvider.Callback() {
                    @Override public void onSuccess(List<AiSubtitleCue> cues) { save(subtitle, cues, callback); }
                    @Override public void onUnavailable() {
                        if (AiSetting.isConfigured()) translateRemote(subtitle, original, callback);
                        else new Handler(Looper.getMainLooper()).post(() -> callback.onError("本地模型不可用，请配置 AI 地址和 API Key"));
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage() == null ? "AI字幕生成失败" : e.getMessage()));
            }
        });
    }

    private static void translateRemote(Sub source, List<AiSubtitleCue> original, Callback callback) {
        try {
            List<AiSubtitleCue> translated = new ArrayList<>();
            final int batchSize = 16;
            for (int start = 0; start < original.size(); start += batchSize) {
                int end = Math.min(start + batchSize, original.size());
                List<String> texts = new ArrayList<>();
                for (int i = start; i < end; i++) texts.add(original.get(i).text());
                List<String> output;
                try {
                    output = AiClient.translateBatch(texts);
                } catch (Exception batchError) {
                    // Some OpenAI-compatible gateways do not handle JSON-array prompts well.
                    // Retry this small group one cue at a time instead of losing the whole job.
                    output = new ArrayList<>();
                    for (String text : texts) output.add(AiClient.translate(text));
                }
                for (int i = 0; i < output.size(); i++) {
                    AiSubtitleCue cue = original.get(start + i);
                    translated.add(new AiSubtitleCue(cue.startMs(), cue.endMs(), output.get(i)));
                }
            }
            save(source, translated, callback);
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage() == null ? "AI字幕生成失败" : e.getMessage()));
        }
    }

    private static void save(Sub source, List<AiSubtitleCue> translated, Callback callback) {
        try {
            File file = new File(App.get().getCacheDir(), "ai-" + Integer.toHexString(source.getUrl().hashCode()) + ".srt");
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(AiSubtitleParser.toSrt(translated).getBytes(StandardCharsets.UTF_8)); }
            Sub result = Sub.from("AI 中文字幕", Uri.fromFile(file).toString(), "zh", "application/x-subrip");
            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(result, translated));
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage() == null ? "AI字幕缓存失败" : e.getMessage()));
        }
    }

    private static Sub findEmbeddedSubtitle(String mediaUrl, Map<String, String> headers) {
        if (mediaUrl == null || !mediaUrl.toLowerCase().contains("m3u8")) return null;
        try {
            String playlist = read(mediaUrl, headers);
            Matcher matcher = HLS_SUBTITLE.matcher(playlist);
            if (!matcher.find()) return null;
            String subtitleUrl = URI.create(mediaUrl).resolve(matcher.group(1)).toString();
            return Sub.from("内嵌字幕", subtitleUrl, "", "text/vtt");
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String read(String url) throws Exception {
        return read(url, new HashMap<>());
    }

    private static String read(String url, Map<String, String> headers) throws Exception {
        if (url.startsWith("file://") || url.startsWith("content://")) {
            Uri uri = Uri.parse(url);
            try (InputStream input = url.startsWith("content://")
                    ? App.get().getContentResolver().openInputStream(uri)
                    : new java.io.FileInputStream(new File(uri.getPath()))) {
                if (input == null) throw new IllegalStateException("无法打开字幕文件");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return output.toString(StandardCharsets.UTF_8.name());
            }
        }
        okhttp3.Request.Builder request = new okhttp3.Request.Builder().url(url).get();
        if (headers != null) headers.forEach((key, value) -> {
            if (key != null && value != null) request.header(key, value);
        });
        try (okhttp3.Response response = OkHttp.client().newCall(request.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("无法读取字幕");
            String text = response.body().string();
            if (text.isEmpty()) throw new IllegalStateException("无法读取字幕");
            return text;
        }
    }
}
