package com.fongmi.android.tv.download;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Scheduler;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.DownloadsActivity;
import com.fongmi.android.tv.utils.Notify;

import java.util.List;

public final class VideoDownloadService extends DownloadService {
    private static final int NOTIFICATION_ID = 4108;
    private final DownloadNotificationHelper helper = new DownloadNotificationHelper(this, Notify.DEFAULT);

    public VideoDownloadService() {
        super(NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, Notify.DEFAULT, 0, 0);
    }

    @Override
    protected DownloadManager getDownloadManager() {
        return VideoDownloads.manager();
    }

    @Override
    protected Scheduler getScheduler() {
        return null;
    }

    @Override
    protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
        PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(this, DownloadsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return helper.buildProgressNotification(this, NOTIFICATION_ID, intent, "视频下载", downloads, notMetRequirements);
    }
}
