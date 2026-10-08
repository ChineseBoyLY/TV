package com.fongmi.android.tv.download;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import okhttp3.Request;
import okhttp3.Response;

/** Shared Media3 offline download manager for the mobile personal build. */
public final class VideoDownloads {
    private static final String PREFS = "personal_downloads";
    private static final String HEADERS = "headers";
    private static final String DOWNLOAD_DIR = "downloads";
    private static final long MAX_CACHE_BYTES = 20L * 1024 * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static StandaloneDatabaseProvider database;
    private static Cache cache;
    private static DownloadManager manager;

    private VideoDownloads() {
    }

    public static synchronized DownloadManager manager() {
        if (manager != null) return manager;
        Context context = App.get();
        database = new StandaloneDatabaseProvider(context);
        cache = new SimpleCache(Path.files(DOWNLOAD_DIR), new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), database);
        manager = new DownloadManager(context, database, cache, upstream(), Executors.newFixedThreadPool(2));
        manager.setMaxParallelDownloads(2);
        return manager;
    }

    public static synchronized Cache cache() {
        manager();
        return cache;
    }

    private static DataSource.Factory upstream() {
        HttpDataSource.Factory http = new OkHttpDataSource.Factory(OkHttp.player()).setDefaultRequestProperties(loadHeaders());
        return new DefaultDataSource.Factory(App.get(), http);
    }

    public static void prepare(@NonNull MediaItem item, String title, Map<String, String> headers, Consumer<String> callback) {
        if (item.localConfiguration == null || item.localConfiguration.uri == null) {
            post(callback, "当前视频没有可下载地址");
            return;
        }
        String url = item.localConfiguration.uri.toString();
        EXECUTOR.execute(() -> {
            try {
                String playlist = probe(url, headers);
                if (playlist != null && !playlist.contains("#EXT-X-ENDLIST")) {
                    post(callback, "直播清单尚未结束，不能下载");
                    return;
                }
                saveHeaders(headers);
                String id = id(url);
                DownloadRequest request = new DownloadRequest.Builder(id, Uri.parse(url))
                        .setMimeType(item.localConfiguration.mimeType)
                        .setData(App.gson().toJson(new Info(title, headers)).getBytes(StandardCharsets.UTF_8))
                        .build();
                androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(App.get(), VideoDownloadService.class, request, true);
                post(callback, "已加入下载列表");
            } catch (Exception error) {
                post(callback, error.getMessage() == null ? "下载地址检查失败" : error.getMessage());
            }
        });
    }

    /** Returns playlist text for HLS, or null for a regular media URL. */
    private static String probe(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).head();
        if (headers != null) headers.forEach((key, value) -> { if (key != null && value != null) builder.header(key, value); });
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("播放地址不可用: " + response.code());
            String type = response.header("Content-Type", "");
            boolean playlist = url.toLowerCase().contains(".m3u8") || type.toLowerCase().contains("mpegurl");
            if (type.toLowerCase().contains("text/html")) throw new IOException("播放地址已失效");
            if (!playlist) return null;
        }
        Request.Builder playlistRequest = new Request.Builder().url(url).get();
        if (headers != null) headers.forEach((key, value) -> { if (key != null && value != null) playlistRequest.header(key, value); });
        try (Response response = OkHttp.client().newCall(playlistRequest.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("无法读取下载清单");
            String text = response.body().string();
            if (!text.contains("#EXTM3U")) throw new IOException("下载地址不是有效视频");
            return text;
        }
    }

    public static List<Download> list() throws IOException {
        List<Download> result = new ArrayList<>();
        DownloadCursor cursor = manager().getDownloadIndex().getDownloads();
        try {
            while (cursor.moveToNext()) result.add(cursor.getDownload());
        } finally {
            cursor.close();
        }
        return result;
    }

    public static Download find(String id) throws IOException {
        return manager().getDownloadIndex().getDownload(id);
    }

    public static void pause(String id) {
        manager().setStopReason(id, Download.STOP_REASON_NONE + 1);
    }

    public static void resume(Download download) {
        if (download == null) return;
        manager().setStopReason(download.request.id, Download.STOP_REASON_NONE);
        manager().resumeDownloads();
    }

    public static void remove(String id) {
        manager().removeDownload(id);
    }

    public static Info info(DownloadRequest request) {
        if (request == null || request.data == null) return new Info("视频", Collections.emptyMap());
        try {
            Info info = App.gson().fromJson(new String(request.data, StandardCharsets.UTF_8), Info.class);
            return info == null ? new Info("视频", Collections.emptyMap()) : info;
        } catch (Exception ignored) {
            return new Info("视频", Collections.emptyMap());
        }
    }

    public static MediaItem mediaItem(Download download) {
        Info info = info(download.request);
        return new MediaItem.Builder().setUri(download.request.uri).setMediaId(download.request.id)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(info.title).build()).build();
    }

    public static CacheDataSource.Factory offlineSource() {
        return new CacheDataSource.Factory().setCache(cache()).setUpstreamDataSourceFactory(new DefaultDataSource.Factory(App.get(), new FileDataSource.Factory())).setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE);
    }

    private static String id(String url) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static void saveHeaders(Map<String, String> headers) {
        Map<String, String> safe = headers == null ? Collections.emptyMap() : new LinkedHashMap<>(headers);
        App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(HEADERS, App.gson().toJson(safe)).apply();
    }

    private static Map<String, String> loadHeaders() {
        String value = App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HEADERS, "{}");
        try {
            return App.gson().fromJson(value, new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static void post(Consumer<String> callback, String message) {
        MAIN.post(() -> { if (callback != null) callback.accept(message); });
    }

    public static final class Info {
        public String title;
        public Map<String, String> headers;

        public Info(String title, Map<String, String> headers) {
            this.title = title == null || title.isBlank() ? "视频" : title;
            this.headers = headers == null ? Collections.emptyMap() : new LinkedHashMap<>(headers);
        }
    }
}
