package com.fongmi.android.tv.net;

import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.bean.WebResource;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/** Searches inside the public websites listed by the guide. */
public final class GuideContentSearch {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8);
    private static final Pattern LINK = Pattern.compile("(?is)<a[^>]+href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>");

    private GuideContentSearch() {}

    public interface Callback {
        void onStatus(String status);
        void onResult(List<WebResource> items);
        void onError(String message);
    }

    public static void search(@NonNull String keyword, @NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                List<WebResource> sources = GuideCatalog.all().stream().filter(item -> item.getCategory().equals("在线影视") || item.getCategory().equals("网盘搜索") || item.getCategory().equals("磁力BT")).toList();
                callback.onStatus("正在搜索 " + sources.size() + " 个精选网站…");
                ExecutorCompletionService<List<WebResource>> completion = new ExecutorCompletionService<>(EXECUTOR);
                for (WebResource source : sources) completion.submit(() -> searchSource(source, keyword));
                LinkedHashMap<String, WebResource> result = new LinkedHashMap<>();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(18);
                for (int i = 0; i < sources.size(); i++) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    Future<List<WebResource>> future = completion.poll(remaining, TimeUnit.NANOSECONDS);
                    if (future == null) break;
                    List<WebResource> items = future.get();
                    for (WebResource item : items) result.putIfAbsent(item.getUrl(), item);
                }
                callback.onResult(new ArrayList<>(result.values()));
            } catch (Exception e) {
                callback.onError("精选网站搜索失败");
            }
        });
    }

    private static List<WebResource> searchSource(WebResource source, String keyword) {
        LinkedHashMap<String, WebResource> result = new LinkedHashMap<>();
        String base = source.getUrl();
        String encoded = Uri.encode(keyword);
        String[] urls = new String[]{
                base + (base.contains("?") ? "&" : "?") + "wd=" + encoded,
                base + (base.contains("?") ? "&" : "?") + "q=" + encoded,
                base + (base.endsWith("/") ? "" : "/") + "search?keyword=" + encoded,
                base + (base.endsWith("/") ? "" : "/") + "search?wd=" + encoded,
                base + (base.endsWith("/") ? "" : "/") + "?s=" + encoded
        };
        for (String url : urls) {
            try {
                String html = get(url);
                Matcher matcher = LINK.matcher(html);
                while (matcher.find() && result.size() < 8) {
                    String title = clean(matcher.group(2));
                    if (title.length() < 2 || (!title.contains(keyword) && !matcher.group(1).contains(encoded))) continue;
                    String target = new java.net.URL(new java.net.URL(url), matcher.group(1)).toString();
                    if (!target.startsWith("http")) continue;
                    result.putIfAbsent(target, new WebResource(title, "来自 " + source.getTitle() + " · " + source.getCategory(), source.getCategory(), target, true));
                }
                if (!result.isEmpty()) break;
            } catch (Exception ignored) {
                // An unavailable website does not block other curated sources.
            }
        }
        return new ArrayList<>(result.values());
    }

    private static String get(String url) throws IOException {
        Request request = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 TV-Personal").build();
        try (Response response = OkHttp.client().newBuilder().callTimeout(4, TimeUnit.SECONDS).build().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException();
            return response.body().string();
        }
    }

    private static String clean(String text) {
        return Html.fromHtml(text == null ? "" : text, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
    }
}
