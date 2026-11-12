package com.ganzithon.teolhanol.domain.wool.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 조언(털) 보내기 요청 (WOL-01). 길이(앞뒤 공백 제거 후 1~100자)는 정책 값으로 서비스에서 검사합니다. */
public record WoolSendRequest(
        @Schema(description = "조언·응원 메시지 (앞뒤 공백 제거 후 1~100자)", example = "천천히 정해도 괜찮아요. 응원할게요.")
        String content,
        @Schema(description = "어조 주의(SUGGEST)를 받은 뒤 '그대로 보내기'를 골랐으면 true. 빠지면 false",
                example = "false")
        Boolean sendAsIs
) {
}
