package com.ganzithon.teolhanol.domain.worry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 고민 올리기 요청 (FRM-04). 길이(앞뒤 공백 제거 후 1~200자)는 정책 값으로 서비스에서 검사합니다. */
public record WorryPostRequest(
        @Schema(description = "고민 본문 (앞뒤 공백 제거 후 1~200자)", example = "진로를 정하지 못해서 매일 불안해요.")
        String content
) {
}
