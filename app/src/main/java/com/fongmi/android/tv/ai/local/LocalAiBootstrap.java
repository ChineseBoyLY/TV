package com.fongmi.android.tv.ai.local;

import android.content.Context;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Starts the one-time model check/download when the app process starts. */
public final class LocalAiBootstrap {
    private static final String TAG = "LocalAiBootstrap";
    private static boolean started;
    private static boolean dialogShowing;

    private LocalAiBootstrap() {}

    public static synchronized void start(Context context) {
        Log.i(TAG, "startup model check; supported=" + LocalMnnEngine.isSupported());
        if (started || !LocalMnnEngine.isSupported()) return;
        started = true;
        LocalModelManager models = new LocalModelManager(context.getApplicationContext());
        if (models.isLlmReady()) {
            Log.i(TAG, "local translation model is ready");
            return;
        }
        request(context, models, null);
    }

    public static synchronized void request(Context context, LocalModelManager.Callback callback) {
        request(context, new LocalModelManager(context.getApplicationContext()), callback);
    }

    private static void request(Context context, LocalModelManager models, LocalModelManager.Callback callback) {
        if (models.isLlmReady()) {
            if (callback != null) callback.onReady();
            return;
        }
        if (!(context instanceof Activity activity) || activity.isFinishing()) {
            if (callback != null) callback.onError("请打开下载确认窗口");
            return;
        }
        if (dialogShowing) return;
        dialogShowing = true;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("下载本地 AI 模型")
                .setMessage("本地中文字幕模型约 480MB，仅在你确认后下载。下载完成后可离线翻译。")
                .setNegativeButton("稍后", (dialog, which) -> dialogShowing = false)
                .setPositiveButton("开始下载", (dialog, which) -> {
                    dialogShowing = false;
                    showProgress(activity, models, callback);
                }).setOnCancelListener(dialog -> dialogShowing = false).show();
    }

    private static void showProgress(Activity activity, LocalModelManager models, LocalModelManager.Callback callback) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 8, 48, 8);
        TextView text = new TextView(activity);
        text.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        box.addView(text);
        box.addView(progress);
        var dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("正在下载本地 AI 模型")
                .setView(box)
                .setCancelable(false)
                .show();
        Handler main = new Handler(Looper.getMainLooper());
        models.ensureLlm(new LocalModelManager.Callback() {
            @Override public void onReady() {
                main.post(() -> { dialog.dismiss(); if (callback != null) callback.onReady(); });
            }

            @Override public void onError(String message) {
                main.post(() -> { dialog.dismiss(); if (callback != null) callback.onError(message); });
            }

            @Override public void onProgress(String name, long downloaded, long total) {
                main.post(() -> {
                    if (total > 0) progress.setProgress((int) Math.min(1000, downloaded * 1000 / total));
                    String size = String.format(java.util.Locale.US, "%.1f / %.1f MB", downloaded / 1048576f, Math.max(downloaded, total) / 1048576f);
                    text.setText(name + "\n" + size);
                });
            }
        });
    }
}
