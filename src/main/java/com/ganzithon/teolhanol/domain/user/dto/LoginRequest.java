package com.ganzithon.teolhanol.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 로그인 요청 (ACC-02) */
public record LoginRequest(
        @Schema(example = "sheep@example.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @Schema(example = "warmwool123")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
