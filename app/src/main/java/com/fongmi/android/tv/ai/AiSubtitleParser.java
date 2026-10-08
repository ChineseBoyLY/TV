package com.fongmi.android.tv.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiSubtitleParser {
    private static final Pattern TIME = Pattern.compile("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s+-->\\s+(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[,.](\\d{1,3})(?:\\s+.*)?");
    private AiSubtitleParser() {}

    public static List<AiSubtitleCue> parse(String source) {
        List<AiSubtitleCue> result = new ArrayList<>();
        String[] blocks = source.replace("\\ufeff", "").replace("\\r", "").split("\\n\\s*\\n");
        for (String block : blocks) {
            String[] lines = block.split("\\n");
            int timeLine = -1;
            Matcher matcher = null;
            for (int i = 0; i < lines.length; i++) {
                Matcher candidate = TIME.matcher(lines[i].trim());
                if (candidate.find()) { timeLine = i; matcher = candidate; break; }
            }
            if (timeLine < 0 || matcher == null) continue;
            StringBuilder text = new StringBuilder();
            for (int i = timeLine + 1; i < lines.length; i++) {
                String line = lines[i].replaceAll("<[^>]+>", "").trim();
                if (line.equalsIgnoreCase("WEBVTT") || line.startsWith("NOTE")) continue;
                if (!line.isEmpty()) { if (text.length() > 0) text.append('\n'); text.append(line); }
            }
            if (text.length() > 0) result.add(new AiSubtitleCue(time(matcher, 1), time(matcher, 5), text.toString()));
        }
        return result;
    }

    public static String toSrt(List<AiSubtitleCue> cues) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cues.size(); i++) {
            AiSubtitleCue cue = cues.get(i);
            out.append(i + 1).append('\n').append(format(cue.startMs())).append(" --> ").append(format(cue.endMs())).append('\n').append(cue.text()).append("\n\n");
        }
        return out.toString();
    }

    private static long time(Matcher m, int offset) {
        String hours = m.group(offset);
        long h = hours == null ? 0 : Long.parseLong(hours);
        long min = Long.parseLong(m.group(offset + 1));
        long sec = Long.parseLong(m.group(offset + 2));
        String millis = m.group(offset + 3);
        long ms = Long.parseLong(millis) * (millis.length() == 1 ? 100 : millis.length() == 2 ? 10 : 1);
        return ((h * 60 + min) * 60 + sec) * 1000 + ms;
    }

    private static String format(long ms) {
        long h = ms / 3600000; ms %= 3600000;
        long m = ms / 60000; ms %= 60000;
        long s = ms / 1000; ms %= 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms);
    }
}
