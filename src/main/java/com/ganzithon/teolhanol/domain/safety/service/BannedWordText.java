package com.ganzithon.teolhanol.domain.safety.service;

/**
 * 금칙어 검사용 글. 소문자로 바꾼 원문({@link #lowered})과 띄어쓰기를 뺀 정규화 문자열({@link #normalized}),
 * 정규화 문자열의 각 글자가 원문 어디에 있었는지를 함께 둡니다. 소문자 변환은 글자 단위라 길이가 바뀌지 않습니다.
 */
final class BannedWordText {

    final String lowered;
    final String normalized;
    private final int[] originIndex;

    private BannedWordText(String lowered, String normalized, int[] originIndex) {
        this.lowered = lowered;
        this.normalized = normalized;
        this.originIndex = originIndex;
    }

    static BannedWordText of(String text) {
        String lowered = lower(text);
        StringBuilder normalized = new StringBuilder(lowered.length());
        int[] originIndex = new int[lowered.length()];
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if (!isSpace(c)) {
                originIndex[normalized.length()] = i;
                normalized.append(c);
            }
        }
        return new BannedWordText(lowered, normalized.toString(), originIndex);
    }

    /** 정규화 문자열 위치를 원문 위치로 바꿉니다. */
    int origin(int normalizedIndex) {
        return originIndex[normalizedIndex];
    }

    /** 띄어쓰기(정규식 \s와 같은 문자)를 빼고 소문자로 바꿉니다. */
    static String normalize(String s) {
        return of(s).normalized;
    }

    static String lower(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }
}
