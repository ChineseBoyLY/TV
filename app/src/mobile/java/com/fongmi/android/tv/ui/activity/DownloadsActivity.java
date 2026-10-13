package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentValues;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.common.util.Clock;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.transformer.DefaultAssetLoaderFactory;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import com.fongmi.android.tv.download.VideoDownloads;
import com.fongmi.android.tv.download.VideoDownloadService;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public final class DownloadsActivity extends AppCompatActivity {
    private static final java.util.concurrent.ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TextView heading;
    private DownloadManager manager;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Transformer transformer;
    private android.app.AlertDialog mergeDialog;
    private ProgressBar mergeProgress;
    private TextView mergeLabel;
    private File mergeOutput;
    private final ProgressHolder mergeProgressHolder = new ProgressHolder();
    private final Runnable mergeTicker = new Runnable() {
        @Override public void run() {
            if (transformer == null || mergeDialog == null || !mergeDialog.isShowing()) return;
            int state = transformer.getProgress(mergeProgressHolder);
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                mergeProgress.setIndeterminate(false);
                mergeProgress.setProgress(mergeProgressHolder.progress);
                mergeLabel.setText("正在合成视频… " + mergeProgressHolder.progress + "%");
            } else if (state == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                mergeProgress.setIndeterminate(true);
                mergeLabel.setText("正在读取已缓存的视频分片…");
            }
            progressHandler.postDelayed(this, 500);
        }
    };
    private final Runnable progressTicker = new Runnable() {
        @Override public void run() {
            if (manager == null || isFinishing() || isDestroyed()) return;
            // onDownloadChanged is a state callback, not a periodic progress callback.
            // Read the manager's live progress snapshots so both the bar and rate refresh
            // while a single download remains in STATE_DOWNLOADING.
            for (Download download : manager.getCurrentDownloads()) {
                DownloadRow row = rows.get(download.request.id);
                if (row != null) row.update(download);
            }
            progressHandler.postDelayed(this, 500);
        }
    };
    private final Map<String, DownloadRow> rows = new HashMap<>();
    private final DownloadManager.Listener listener = new DownloadManager.Listener() {
        @Override public void onInitialized(DownloadManager manager) { load(); }
        @Override public void onDownloadChanged(DownloadManager manager, Download download, Exception error) {
            runOnUiThread(() -> {
                DownloadRow row = rows.get(download.request.id);
                if (row == null) load();
                else row.update(download);
            });
        }
        @Override public void onDownloadRemoved(DownloadManager manager, Download download) {
            runOnUiThread(() -> {
                DownloadRow row = rows.remove(download.request.id);
                if (row != null) list.removeView(row.root);
                heading.setText("下载管理（" + rows.size() + "）");
                showEmptyIfNeeded();
            });
        }
    };

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadsActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setTitle("下载管理");
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 24, 24, 24);
        list.setBackgroundColor(MaterialColors.getColor(list, com.google.android.material.R.attr.colorSurface));
        heading = text("下载管理");
        heading.setTextSize(20);
        heading.setPadding(dp(8), dp(8), dp(8), dp(16));
        list.addView(heading);
        setContentView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list == null) return;
        manager = VideoDownloads.manager();
        manager.addListener(listener);
        DownloadService.sendResumeDownloads(this, VideoDownloadService.class, true);
        load();
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.post(progressTicker);
    }

    @Override
    protected void onPause() {
        progressHandler.removeCallbacks(progressTicker);
        if (manager != null) manager.removeListener(listener);
        super.onPause();
    }

    private void load() {
        EXECUTOR.execute(() -> {
            try {
                List<Download> downloads = VideoDownloads.list();
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) render(downloads); });
            } catch (Exception error) {
                runOnUiThread(() -> Notify.show(error.getMessage() == null ? "读取下载列表失败" : error.getMessage()));
            }
        });
    }

    private void render(List<Download> downloads) {
        if (list.getChildCount() > 1) list.removeViews(1, list.getChildCount() - 1);
        rows.clear();
        heading.setText("下载管理（" + downloads.size() + "）");
        if (downloads.isEmpty()) {
            showEmptyIfNeeded();
            return;
        }
        for (Download download : downloads) {
            DownloadRow row = new DownloadRow(download);
            rows.put(download.request.id, row);
            list.addView(row.root);
        }
    }

    private void showEmptyIfNeeded() {
        if (rows.isEmpty() && list.getChildCount() == 1) list.addView(text("暂无下载任务"));
    }

    private void open(Download download) {
        if (download.state == Download.STATE_STOPPED) VideoDownloads.resume(download);
        if (download.state != Download.STATE_COMPLETED) {
            Notify.show("视频尚未下载完成");
            return;
        }
        startActivity(new Intent(this, OfflineVideoActivity.class).putExtra("download_id", download.request.id));
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface));
        view.setTextSize(16);
        view.setPadding(16, 20, 16, 20);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class DownloadRow {
        private final String id;
        private final LinearLayout root;
        private final TextView title;
        private final TextView detail;
        private final TextView speed;
        private final ProgressBar progress;
        private final MaterialButton pause;
        private final MaterialButton resume;
        private final MaterialButton retry;
        private final MaterialButton play;
        private final MaterialButton merge;
        private Download current;
        private long lastBytes = -1;
        private long lastSampleAt;
        private long bytesPerSecond;

        DownloadRow(Download download) {
            id = download.request.id;
            root = new LinearLayout(DownloadsActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), dp(12), dp(16), dp(8));
            GradientDrawable background = new GradientDrawable();
            background.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceVariant));
            background.setCornerRadius(dp(12));
            root.setBackground(background);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, 0, 0, dp(10));
            root.setLayoutParams(cardParams);

            title = text(VideoDownloads.info(download.request).title);
            title.setTextSize(16);
            title.setPadding(0, 0, 0, dp(4));
            detail = text("");
            detail.setTextSize(13);
            detail.setPadding(0, 0, 0, dp(6));
            speed = text("");
            speed.setTextSize(12);
            speed.setPadding(0, 0, 0, dp(4));
            speed.setTextColor(MaterialColors.getColor(root, android.R.attr.colorAccent));
            progress = new ProgressBar(DownloadsActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgressTintList(ColorStateList.valueOf(MaterialColors.getColor(root, android.R.attr.colorAccent)));
            LinearLayout controls = new LinearLayout(DownloadsActivity.this);
            controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            pause = button("暂停");
            resume = button("继续");
            retry = button("重试");
            play = button("播放");
            merge = button("合成");
            MaterialButton delete = button("删除");
            controls.addView(pause);
            controls.addView(resume);
            controls.addView(retry);
            controls.addView(play);
            controls.addView(merge);
            controls.addView(delete);
            root.addView(title);
            root.addView(detail);
            root.addView(speed);
            root.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
            root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            pause.setOnClickListener(v -> VideoDownloads.pause(id));
            resume.setOnClickListener(v -> {
                try { VideoDownloads.resume(VideoDownloads.find(id)); }
                catch (Exception error) { Notify.show(error.getMessage()); }
            });
            retry.setOnClickListener(v -> retry(current));
            play.setOnClickListener(v -> open(current));
            merge.setOnClickListener(v -> merge(current));
            delete.setOnClickListener(v -> VideoDownloads.remove(id));
            update(download);
        }

        void update(Download download) {
            current = download;
            long now = android.os.SystemClock.elapsedRealtime();
            long bytes = download.getBytesDownloaded();
            if (download.state == Download.STATE_DOWNLOADING && lastBytes >= 0 && now > lastSampleAt) {
                bytesPerSecond = Math.max(0, (bytes - lastBytes) * 1000 / (now - lastSampleAt));
            } else if (download.state != Download.STATE_DOWNLOADING) {
                bytesPerSecond = 0;
            }
            lastBytes = bytes;
            lastSampleAt = now;
            float percent = download.getPercentDownloaded();
            boolean known = percent >= 0;
            progress.setIndeterminate(download.state == Download.STATE_DOWNLOADING && !known);
            progress.setProgress(known ? Math.min(1000, Math.max(0, Math.round(percent * 10))) : 0);
            String amount = android.text.format.Formatter.formatShortFileSize(DownloadsActivity.this, download.getBytesDownloaded());
            if (download.state == Download.STATE_COMPLETED) detail.setText("已完成 · " + amount);
            else if (download.state == Download.STATE_DOWNLOADING) detail.setText("下载中 · " + (known ? String.format(java.util.Locale.getDefault(), "%.0f%%", percent) : "计算进度中") + " · " + amount);
            else if (download.state == Download.STATE_FAILED) detail.setText("下载失败 · " + amount);
            else if (download.state == Download.STATE_STOPPED) detail.setText("已暂停 · " + (known ? String.format(java.util.Locale.getDefault(), "%.0f%%", percent) : "0%") + " · " + amount);
            else detail.setText("等待下载 · " + (known ? String.format(java.util.Locale.getDefault(), "%.0f%%", percent) : "0%") + " · " + amount);
            if (download.state == Download.STATE_DOWNLOADING) {
                speed.setVisibility(View.VISIBLE);
                speed.setText("↓ " + android.text.format.Formatter.formatShortFileSize(DownloadsActivity.this, bytesPerSecond) + "/s");
            } else {
                speed.setVisibility(View.GONE);
            }
            pause.setVisibility(download.state == Download.STATE_QUEUED || download.state == Download.STATE_DOWNLOADING ? View.VISIBLE : View.GONE);
            resume.setVisibility(download.state == Download.STATE_STOPPED ? View.VISIBLE : View.GONE);
            retry.setVisibility(download.state == Download.STATE_FAILED ? View.VISIBLE : View.GONE);
            play.setVisibility(download.state == Download.STATE_COMPLETED ? View.VISIBLE : View.GONE);
            merge.setVisibility(download.state == Download.STATE_COMPLETED && download.request.mimeType != null
                    && download.request.mimeType.toLowerCase(java.util.Locale.ROOT).contains("mpegurl") ? View.VISIBLE : View.GONE);
        }

        private MaterialButton button(String label) {
            MaterialButton button = new MaterialButton(DownloadsActivity.this);
            button.setText(label);
            button.setMinWidth(0);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
            params.setMargins(dp(2), 0, 0, 0);
            button.setLayoutParams(params);
            return button;
        }

        private void retry(Download download) {
            DownloadService.sendAddDownload(DownloadsActivity.this, VideoDownloadService.class, download.request, true);
        }

        private void merge(Download download) {
            if (transformer != null) {
                Notify.show("已有视频正在合成");
                return;
            }
            try {
                File directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                if (directory == null || (!directory.exists() && !directory.mkdirs())) throw new Exception("无法创建视频保存目录");
                String name = VideoDownloads.info(download.request).title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
                if (name.isEmpty()) name = "视频";
                mergeOutput = new File(directory, name + ".mp4");
                if (mergeOutput.exists()) mergeOutput = new File(directory, name + "_" + System.currentTimeMillis() + ".mp4");

                LinearLayout body = new LinearLayout(DownloadsActivity.this);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setPadding(dp(24), dp(8), dp(24), dp(8));
                mergeLabel = text("正在准备合成…");
                mergeLabel.setTextSize(14);
                mergeProgress = new ProgressBar(DownloadsActivity.this, null, android.R.attr.progressBarStyleHorizontal);
                mergeProgress.setMax(100);
                body.addView(mergeLabel);
                body.addView(mergeProgress, new LinearLayout.LayoutParams(-1, dp(8)));
                mergeDialog = new android.app.AlertDialog.Builder(DownloadsActivity.this)
                        .setTitle("合成视频")
                        .setView(body)
                        .setNegativeButton("取消", (dialog, which) -> cancelMerge())
                        .create();
                mergeDialog.setCancelable(false);
                mergeDialog.setCanceledOnTouchOutside(false);
                mergeDialog.setOnCancelListener(dialog -> cancelMerge());
                mergeDialog.show();

                DefaultAssetLoaderFactory assetLoader = new DefaultAssetLoaderFactory(
                        DownloadsActivity.this,
                        new DefaultDecoderFactory.Builder(DownloadsActivity.this).build(),
                        Clock.DEFAULT,
                        VideoDownloads.offlineMediaSourceFactory(),
                        new DataSourceBitmapLoader.Builder(DownloadsActivity.this).build());
                transformer = new Transformer.Builder(DownloadsActivity.this)
                        .setAssetLoaderFactory(assetLoader)
                        .addListener(new Transformer.Listener() {
                            @Override public void onCompleted(androidx.media3.transformer.Composition composition, androidx.media3.transformer.ExportResult result) {
                                publishMerge();
                            }
                            @Override public void onError(androidx.media3.transformer.Composition composition, androidx.media3.transformer.ExportResult result, androidx.media3.transformer.ExportException error) {
                                finishMerge(false, error.getMessage());
                            }
                        }).build();
                transformer.start(VideoDownloads.mediaItem(download), mergeOutput.getAbsolutePath());
                progressHandler.post(mergeTicker);
            } catch (Exception error) {
                finishMerge(false, error.getMessage());
            }
        }

        private void cancelMerge() {
            if (mergeDialog != null && mergeLabel != null && mergeLabel.getText().toString().contains("保存到公共目录")) return;
            progressHandler.removeCallbacks(mergeTicker);
            if (transformer != null) transformer.cancel();
            transformer = null;
            if (mergeOutput != null && mergeOutput.exists()) mergeOutput.delete();
            mergeOutput = null;
        }

        private void finishMerge(boolean success, String message) {
            progressHandler.removeCallbacks(mergeTicker);
            transformer = null;
            if (mergeDialog != null && mergeDialog.isShowing()) mergeDialog.dismiss();
            mergeDialog = null;
            if (success) Notify.show("合成完成：内部存储/Movies/TV-personal/" + (mergeOutput == null ? "" : mergeOutput.getName()));
            else {
                if (mergeOutput != null && mergeOutput.exists()) mergeOutput.delete();
                Notify.show("合成失败" + (message == null || message.isBlank() ? "" : "：" + message));
            }
            mergeOutput = null;
        }

        private void publishMerge() {
            if (mergeOutput == null) {
                finishMerge(false, "合成文件不存在");
                return;
            }
            progressHandler.removeCallbacks(mergeTicker);
            mergeProgress.setIndeterminate(true);
            mergeLabel.setText("正在保存到公共目录…");
            if (mergeDialog != null) {
                mergeDialog.setCancelable(false);
                mergeDialog.setCanceledOnTouchOutside(false);
                mergeDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            }
            File source = mergeOutput;
            EXECUTOR.execute(() -> {
                Uri destination = null;
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TV-personal");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    destination = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                    if (destination == null) throw new java.io.IOException("无法创建公共视频文件");
                    try (InputStream input = new java.io.FileInputStream(source);
                         OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                        if (output == null) throw new java.io.IOException("无法写入公共视频文件");
                        byte[] buffer = new byte[1024 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(destination, ready, null, null);
                    source.delete();
                    runOnUiThread(() -> finishMerge(true, null));
                } catch (Exception error) {
                    if (destination != null) getContentResolver().delete(destination, null, null);
                    runOnUiThread(() -> finishMerge(false, error.getMessage()));
                }
            });
        }
    }
}
