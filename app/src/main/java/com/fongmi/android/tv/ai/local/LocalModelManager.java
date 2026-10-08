package com.fongmi.android.tv.ai.local;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Request;
import okhttp3.Response;

/** Owns model paths and keeps the native engine out of the process until explicitly requested. */
public final class LocalModelManager {
    private static final String LLM_DIR = "qwen3.5-0.8b";
    private final File root;
    private static final Object LOCK = new Object();
    private static final ExecutorService DOWNLOAD = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean();
    private static final List<Callback> WAITERS = new ArrayList<>();
    private static final String MODEL_BASE = "https://modelscope.cn/models/yuqingteck/mommy-story-on-device-models/resolve/master/qwen3.5-0.8b/";
    private static final String[] LLM_FILES = {"llm.mnn.weight", "tokenizer.txt", "llm.mnn.json", "llm.mnn", "llm_config.json", "config.json"};

    public LocalModelManager(@NonNull Context context) {
        root = new File(context.getFilesDir(), "local-ai/models");
    }

    public File root() { return root; }
    public File llmDir() { return new File(root, LLM_DIR); }
    public File llmConfig() { return new File(llmDir(), "config.json"); }
    public boolean isLlmReady() {
        for (String name : LLM_FILES) {
            File file = new File(llmDir(), name);
            if (!file.isFile() || file.length() == 0) return false;
        }
        return true;
    }

    public void ensureLlm(Callback callback) {
        if (isLlmReady()) { callback.onReady(); return; }
        synchronized (LOCK) {
            WAITERS.add(callback);
            if (!DOWNLOADING.compareAndSet(false, true)) return;
        }
        DOWNLOAD.execute(() -> {
            boolean success = false;
            String error = null;
            try {
                if (!llmDir().exists() && !llmDir().mkdirs()) throw new IOException("无法创建本地模型目录");
                for (String name : LLM_FILES) download(name);
                success = true;
            } catch (Exception exception) {
                error = exception.getMessage() == null ? "本地模型下载失败" : exception.getMessage();
            }
            List<Callback> callbacks;
            synchronized (LOCK) {
                callbacks = new ArrayList<>(WAITERS);
                WAITERS.clear();
                DOWNLOADING.set(false);
            }
            for (Callback item : callbacks) {
                if (success) item.onReady();
                else item.onError(error);
            }
        });
    }

    private void download(String name) throws IOException {
        File target = new File(llmDir(), name);
        if (target.isFile() && target.length() > 0) return;
        File partial = new File(target.getPath() + ".part");
        Request request = new Request.Builder().url(MODEL_BASE + name).build();
        try (Response response = com.github.catvod.net.OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("下载失败: " + name + " (" + response.code() + ")");
            try (okhttp3.ResponseBody body = response.body(); java.io.InputStream input = body.byteStream(); FileOutputStream output = new FileOutputStream(partial)) {
                long total = body.contentLength();
                long downloaded = 0;
                long lastReport = 0;
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    downloaded += count;
                    if (downloaded - lastReport >= 256 * 1024 || downloaded == total) {
                        lastReport = downloaded;
                        notifyProgress(name, downloaded, total);
                    }
                }
            }
            if (!partial.renameTo(target)) throw new IOException("保存模型失败: " + name);
        }
    }

    private void notifyProgress(String name, long downloaded, long total) {
        List<Callback> callbacks;
        synchronized (LOCK) { callbacks = new ArrayList<>(WAITERS); }
        for (Callback callback : callbacks) callback.onProgress(name, downloaded, total);
    }

    public interface Callback {
        void onReady();
        void onError(String message);
        default void onProgress(String name, long downloaded, long total) {}
    }
}
