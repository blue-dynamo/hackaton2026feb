package com.hackathon.storywriter.util;

/**
 * Shared string utilities for the story-writer pipeline.
 */
public final class Strings {

    private Strings() {
        // utility class – no instances
    }

    /**
     * Returns {@code value} when non-null, otherwise the literal {@code "(not provided)"}.
     *
     * @param value nullable string
     * @return non-null string
     */
    public static String nvl(String value) {
        return value != null ? value : "(not provided)";
    }

    /**
     * Truncates {@code s} to at most {@code maxChars} characters, appending
     * {@code "\n... [truncated]"} when the string was actually trimmed.
     *
     * @param s        nullable string
     * @param maxChars maximum length
     * @return non-null, possibly truncated string
     */
    public static String truncate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() > maxChars ? s.substring(0, maxChars) + "\n... [truncated]" : s;
    }

    /**
     * Strips Markdown code fences (e.g. {@code ```json ... ```}) from a string.
     * If no fences are present the original string is returned unchanged.
     *
     * @param raw raw string that may contain code fences
     * @return clean string without leading/trailing code fences
     */
    public static String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            return trimmed.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return trimmed;
    }
}
