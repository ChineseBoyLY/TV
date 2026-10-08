package com.fongmi.android.tv.ai.local;

import android.os.Build;
import android.util.Log;

import java.util.HashMap;
import java.util.List;

/** Thin Java wrapper around the optional MNN Qwen runtime. */
public final class LocalMnnEngine {
    public interface Listener { boolean onToken(String token); }

    private static final boolean SUPPORTED = Build.VERSION.SDK_INT >= 24 && Build.SUPPORTED_ABIS.length > 0 && "arm64-v8a".equals(Build.SUPPORTED_ABIS[0]);
    private static boolean libraryLoaded;

    static {
        if (SUPPORTED) {
            try { System.loadLibrary("mnn_llm_bridge"); libraryLoaded = true; }
            catch (UnsatisfiedLinkError error) {
                libraryLoaded = false;
                Log.e("LocalMnnEngine", "无法加载本地 MNN 引擎", error);
            }
        }
    }

    public static boolean isSupported() { return SUPPORTED && libraryLoaded; }
    public long loadModel(String configPath) { return isSupported() ? nativeLoadModel(configPath) : -1; }
    public HashMap<String, Object> generate(String system, String user, int maxTokens, Listener listener) { return nativeGenerate(system, user, maxTokens, listener); }
    public void stop() { if (isSupported()) nativeStopGenerate(); }
    public void release() { if (isSupported()) nativeReleaseModel(); }
    public boolean isLoaded() { return isSupported() && nativeIsModelLoaded(); }

    private native long nativeLoadModel(String configPath);
    private native HashMap<String, Object> nativeGenerate(String system, String user, int maxTokens, Listener listener);
    private native HashMap<String, Object> nativeGenerateWithHistory(List<?> history, int maxTokens, Listener listener);
    private native void nativeStopGenerate();
    private native void nativeReleaseModel();
    private native boolean nativeIsModelLoaded();
    private native boolean nativeIsGenerating();
}
