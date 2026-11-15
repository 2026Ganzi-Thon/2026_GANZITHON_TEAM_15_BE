package com.ganzithon.teolhanol.domain.worry.service;

/** 고민 앞부분 미리보기 규칙. 목장 목록과 내 고민 목록이 같은 규칙을 씁니다. */
public final class WorryPreview {

    private static final int PREVIEW_LENGTH = 40;
    private static final String ELLIPSIS = "…";

    private WorryPreview() {
    }

    /** 앞 40자. 넘으면 "…"를 붙입니다. 이모지가 반으로 잘리지 않도록 코드 포인트 기준으로 자릅니다. */
    public static String of(String content) {
        String text = content.strip();
        if (text.codePointCount(0, text.length()) <= PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH)) + ELLIPSIS;
    }
}
