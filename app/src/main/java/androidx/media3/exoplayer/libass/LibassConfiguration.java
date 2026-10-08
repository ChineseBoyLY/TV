package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;

/** Compatibility configuration used when the optional libass extension is absent. */
public final class LibassConfiguration {
    private LibassConfiguration() {}

    public static final class Builder {
        public Builder setFontConfig(@Nullable String value) { return this; }
        public Builder setFontsDirectory(@Nullable String value) { return this; }
        public Builder setDefaultFontFamily(@Nullable String value) { return this; }
        public Builder setMaximumRenderPixels(int value) { return this; }
        public Builder setMaximumGlyphCount(int value) { return this; }
        public Builder setMaximumBitmapCacheSizeMb(int value) { return this; }
        public LibassConfiguration build() { return new LibassConfiguration(); }
    }
}
