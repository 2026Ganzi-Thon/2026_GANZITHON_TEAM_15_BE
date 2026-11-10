package com.ganzithon.teolhanol.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 닉네임 바꾸기 요청 (ACC-05). 규칙은 가입 때와 같고 서비스에서 정책값으로 검사합니다. */
public record NicknameChangeRequest(
        @Schema(example = "포근한 목동")
        @NotNull(message = "닉네임을 입력해 주세요.")
        String nickname
) {
}
