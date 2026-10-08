package androidx.media3.ui.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.libass.LibassPlaybackSession;
import androidx.media3.exoplayer.libass.LibassSubtitleController;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.util.function.Consumer;

/** Standard Media3 subtitle fallback for builds without the optional libass UI extension. */
public final class LibassPlayerViewController {
    public LibassPlayerViewController(ExoPlayer player, LibassPlaybackSession session, LibassSubtitleController controller) {}
    public void bind(PlayerView playerView) {}
    public void setStyleOverride(@Nullable Object style, @Nullable String fontFamily) {}
    public void setSecondarySubtitleViewConfigurator(Consumer<SubtitleView> configurator) {}
    public void close() {}
}
