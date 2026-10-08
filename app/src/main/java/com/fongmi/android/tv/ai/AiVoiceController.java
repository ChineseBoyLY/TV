package com.fongmi.android.tv.ai;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.AiSetting;
import com.fongmi.android.tv.ai.local.LocalVoiceController;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Plays generated speech cues on the video's clock. One controller is shared per app. */
public final class AiVoiceController {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static MediaPlayer media;
    private static PlayerManager player;
    private static List<AiSubtitleCue> cues;
    private static List<File> files;
    private static float originalVolume = 1.0f;
    private static int currentIndex = -1;
    private static final AtomicInteger RUN = new AtomicInteger();

    private AiVoiceController() {}

    public static void start(PlayerManager target, List<AiSubtitleCue> translated) {
        if (!AiSetting.isConfigured()) { stop(); LocalVoiceController.start(target, translated); return; }
        stop();
        final int run = RUN.incrementAndGet();
        player = target; cues = translated;
        EXECUTOR.execute(() -> {
            try {
                List<File> generated = new java.util.ArrayList<>();
                for (AiSubtitleCue cue : translated) {
                    if (run != RUN.get()) return;
                    File file = File.createTempFile("ai-voice-", ".mp3", com.fongmi.android.tv.App.get().getCacheDir());
                    try (FileOutputStream out = new FileOutputStream(file)) { out.write(AiClient.speech(cue.text())); }
                    generated.add(file);
                }
                files = generated;
                HANDLER.post(() -> { if (run == RUN.get() && player != null) { originalVolume = player.getVolume(); player.setVolume(0); tick(); } });
            } catch (Exception e) { HANDLER.post(() -> Notify.show(e.getMessage() == null ? "AI配音生成失败" : e.getMessage())); }
        });
    }

    private static void tick() {
        if (player == null || files == null || cues == null) return;
        long position = player.getPosition();
        int index = -1;
        for (int i = 0; i < cues.size(); i++) if (position >= cues.get(i).startMs() && position < cues.get(i).endMs()) { index = i; break; }
        if (index != currentIndex) {
            currentIndex = index;
            if (index >= 0) play(index);
            else releaseMedia();
        } else if (index >= 0 && media == null) play(index);
        HANDLER.postDelayed(AiVoiceController::tick, 120);
    }

    private static void play(int index) {
        try { releaseMedia(); media = new MediaPlayer(); media.setDataSource(files.get(index).getPath()); media.prepare(); media.start(); }
        catch (Exception ignored) { }
    }

    private static void releaseMedia() {
        if (media != null) { media.release(); media = null; }
    }

    public static void stop() {
        RUN.incrementAndGet();
        LocalVoiceController.stop();
        HANDLER.removeCallbacksAndMessages(null);
        releaseMedia();
        if (player != null) player.setVolume(originalVolume);
        if (files != null) for (File file : files) if (file != null) file.delete();
        player = null; cues = null; files = null;
        currentIndex = -1;
    }
}
