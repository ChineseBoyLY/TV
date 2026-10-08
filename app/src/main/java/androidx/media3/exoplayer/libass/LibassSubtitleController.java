package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.text.SecondaryTextOutput;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import java.util.Collections;
import java.util.List;

/** No-op fallback for the optional secondary subtitle controller. */
public final class LibassSubtitleController {
    public LibassSubtitleController(ExoPlayer player, LibassPlaybackSession session, TrackSelector.Factory factory, SecondaryTextOutput output) {}
    @Nullable public TrackSelectionOverride getPrimaryTextTrackSelectionOverride() { return null; }
    @Nullable public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() { return null; }
    public List<TrackSelectionOverride> getSecondaryTextTrackSelectionOverrides() { return Collections.emptyList(); }
    public boolean isSecondaryTextTrackSuppressed() { return false; }
    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride selection) {}
    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {}
    public void close() {}
}
