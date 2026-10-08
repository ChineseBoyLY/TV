package androidx.media3.exoplayer.libass;

import java.io.File;

/** Typeface-based fallback for external font metadata. */
public final class LibassFontFile {
    private LibassFontFile() {}

    public static String getFamilyName(File file) throws java.io.IOException {
        String name = file == null ? "" : file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
