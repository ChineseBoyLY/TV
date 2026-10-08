package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.exoplayer.offline.Download;

import com.fongmi.android.tv.download.VideoDownloads;
import com.fongmi.android.tv.utils.Notify;

import java.util.List;
import java.util.concurrent.Executors;

public final class DownloadsActivity extends AppCompatActivity {
    private LinearLayout list;

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
        setContentView(list);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) load();
    }

    private void load() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Download> downloads = VideoDownloads.list();
                runOnUiThread(() -> render(downloads));
            } catch (Exception error) {
                runOnUiThread(() -> Notify.show(error.getMessage()));
            }
        });
    }

    private void render(List<Download> downloads) {
        list.removeAllViews();
        if (downloads.isEmpty()) {
            TextView empty = text("暂无下载");
            list.addView(empty);
            return;
        }
        for (Download download : downloads) {
            VideoDownloads.Info info = VideoDownloads.info(download.request);
            TextView row = text(info.title + "\n" + state(download));
            row.setOnClickListener(view -> open(download));
            row.setOnLongClickListener(view -> {
                VideoDownloads.remove(download.request.id);
                load();
                return true;
            });
            list.addView(row);
        }
    }

    private void open(Download download) {
        if (download.state == Download.STATE_STOPPED) VideoDownloads.resume(download);
        if (download.state != Download.STATE_COMPLETED) {
            Notify.show("视频尚未下载完成");
            return;
        }
        startActivity(new Intent(this, OfflineVideoActivity.class).putExtra("download_id", download.request.id));
    }

    private String state(Download download) {
        if (download.state == Download.STATE_COMPLETED) return "已完成 · 长按删除";
        if (download.state == Download.STATE_DOWNLOADING) return String.format("下载中 %.0f%%", download.getPercentDownloaded());
        if (download.state == Download.STATE_FAILED) return "下载失败 · 长按删除";
        if (download.state == Download.STATE_STOPPED) return "已暂停 · 点击继续";
        return "等待下载";
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setPadding(16, 20, 16, 20);
        return view;
    }
}
