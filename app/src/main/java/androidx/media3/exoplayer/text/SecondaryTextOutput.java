package androidx.media3.exoplayer.text;

import androidx.media3.common.text.CueGroup;

/** Drops secondary subtitle cues when the optional secondary renderer is absent. */
public final class SecondaryTextOutput implements TextOutput {
    @Override public void onCues(CueGroup cueGroup) {}
}
