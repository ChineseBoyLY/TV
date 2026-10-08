package com.fongmi.android.tv.ai.local;

import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ai.AiSubtitleCue;
import com.fongmi.android.tv.player.PlayerManager;

import java.util.List;
import java.util.Locale;

/** Offline fallback using the Chinese voice installed on the phone. */
public final class LocalVoiceController implements TextToSpeech.OnInitListener {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static LocalVoiceController instance;
    private final TextToSpeech tts;
    private PlayerManager player;
    private List<AiSubtitleCue> cues;
    private int lastIndex = -1;
    private long lastPosition = -1;
    private float originalVolume = 1.0f;

    private LocalVoiceController() { tts = new TextToSpeech(App.get(), this); }

    public static void start(PlayerManager player, List<AiSubtitleCue> cues) {
        stop();
        instance = new LocalVoiceController();
        instance.player = player;
        instance.cues = cues;
        instance.originalVolume = player.getVolume();
        player.setVolume(0);
    }

    public static void stop() {
        if (instance == null) return;
        HANDLER.removeCallbacksAndMessages(null);
        instance.tts.stop(); instance.tts.shutdown();
        if (instance.player != null) instance.player.setVolume(instance.originalVolume);
        instance = null;
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || instance == null) return;
        tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        tick();
    }

    private void tick() {
        if (instance == null || player == null || cues == null) return;
        long position = player.getPosition();
        if (lastPosition >= 0 && position + 1000 < lastPosition) lastIndex = -1;
        lastPosition = position;
        for (int i = 0; i < cues.size(); i++) {
            AiSubtitleCue cue = cues.get(i);
            if (position >= cue.startMs() && position < cue.endMs() && i != lastIndex) {
                lastIndex = i;
                if (!TextUtils.isEmpty(cue.text())) tts.speak(cue.text(), TextToSpeech.QUEUE_FLUSH, null, "ai-local-" + i);
                break;
            }
        }
        HANDLER.postDelayed(this::tick, 120);
    }
}
