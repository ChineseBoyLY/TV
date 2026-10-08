package com.fongmi.android.tv.ai.local;

import android.content.Context;

import com.fongmi.android.tv.ai.AiSubtitleCue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-first translation provider. It returns false when the optional model is unavailable. */
public final class LocalAiProvider {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private final LocalModelManager models;
    private LocalMnnEngine engine;

    public LocalAiProvider(Context context) { models = new LocalModelManager(context.getApplicationContext()); }
    public boolean isReady() { return LocalMnnEngine.isSupported() && models.isLlmReady(); }

    public void translate(List<AiSubtitleCue> source, Callback callback) {
        if (!LocalMnnEngine.isSupported()) { callback.onUnavailable(); return; }
        if (!models.isLlmReady()) {
            LocalAiBootstrap.request(com.fongmi.android.tv.App.get(), new LocalModelManager.Callback() {
                @Override public void onReady() { translate(source, callback); }
                @Override public void onError(String message) { callback.onUnavailable(); }
            });
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                if (engine == null) engine = new LocalMnnEngine();
                if (!engine.isLoaded() && engine.loadModel(models.llmConfig().getAbsolutePath()) < 0) { callback.onUnavailable(); return; }
                java.util.ArrayList<AiSubtitleCue> out = new java.util.ArrayList<>();
                for (AiSubtitleCue cue : source) {
                    StringBuilder text = new StringBuilder();
                    engine.generate("You translate subtitles into natural Simplified Chinese. Return only the translation.", cue.text(), 256, token -> { text.append(token); return false; });
                    out.add(new AiSubtitleCue(cue.startMs(), cue.endMs(), text.toString().trim()));
                }
                callback.onSuccess(out);
            } catch (Throwable error) { callback.onUnavailable(); }
        });
    }

    public void release() { if (engine != null) { engine.release(); engine = null; } }
    public interface Callback { void onSuccess(List<AiSubtitleCue> cues); void onUnavailable(); }
}
