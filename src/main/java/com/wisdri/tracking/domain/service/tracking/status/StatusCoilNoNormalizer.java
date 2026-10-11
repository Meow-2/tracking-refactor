package com.wisdri.tracking.domain.service.tracking.status;

import java.util.regex.Pattern;

/** 复用现场卷号清洗规则，过滤不可见边界、纯句点及 request color 占位值。 */
public final class StatusCoilNoNormalizer {
    private static final Pattern INVALID_PREFIX = Pattern.compile("^request\\s+color\\b", Pattern.CASE_INSENSITIVE);

    private StatusCoilNoNormalizer() {
    }

    public static String normalize(String value) {
        String trimmed = trimInvisible(value);
        if (trimmed == null || trimmed.isEmpty() || INVALID_PREFIX.matcher(trimmed).find()) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != '.') {
                return trimmed;
            }
        }
        return null;
    }

    public static String trimInvisible(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && invisible(value.charAt(start))) {
            start++;
        }
        while (end > start && invisible(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean invisible(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value)
                || Character.isISOControl(value) || Character.getType(value) == Character.FORMAT;
    }
}
