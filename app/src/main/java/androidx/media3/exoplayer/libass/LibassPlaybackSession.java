package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;

/** No-op fallback for builds that do not bundle FongMi's libass extension. */
public final class LibassPlaybackSession {
    public static final class MediaComponents {
        public final ExtractorsFactory extractorsFactory;
        public final SubtitleParser.Factory subtitleParserFactory;

        public MediaComponents(ExtractorsFactory extractorsFactory, SubtitleParser.Factory subtitleParserFactory) {
            this.extractorsFactory = extractorsFactory;
            this.subtitleParserFactory = subtitleParserFactory;
        }
    }

    public LibassPlaybackSession(LibassConfiguration configuration, boolean enabled) {}
    public boolean isAvailable() { return false; }
    public void setPreloadMediaItem(@Nullable MediaItem item) {}
    public void setBottomPositionFraction(float value) {}
    public void setSecondaryBottomPositionFraction(float value) {}
    public void setFontScale(float value, boolean animated) {}
    @Nullable public Renderer createClockRenderer() { return null; }
    @Nullable public MediaComponents createMediaComponents(MediaItem item, ExtractorsFactory factory) { return null; }
    public void close() {}
}
