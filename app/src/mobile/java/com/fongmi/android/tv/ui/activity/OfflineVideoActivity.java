package com.fongmi.android.tv.ui.activity;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.download.VideoDownloads;

public final class OfflineVideoActivity extends AppCompatActivity {
    private ExoPlayer player;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        PlayerView view = new PlayerView(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(view);
        try {
            Download download = VideoDownloads.find(getIntent().getStringExtra("download_id"));
            if (download == null || download.state != Download.STATE_COMPLETED) {
                finish();
                return;
            }
            player = new ExoPlayer.Builder(this).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(VideoDownloads.offlineSource())).build();
            view.setPlayer(player);
            player.setMediaItem(VideoDownloads.mediaItem(download));
            player.prepare();
            player.play();
        } catch (Exception error) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        super.onDestroy();
    }
}
