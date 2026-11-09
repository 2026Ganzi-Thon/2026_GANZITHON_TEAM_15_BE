package com.ganzithon.teolhanol.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 회원 가입 요청 (ACC-01). 비밀번호·닉네임 세부 규칙은 서비스에서 정책값으로 검사합니다. */
public record SignupRequest(
        @Schema(example = "sheep@example.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 한 번 더 확인해 주세요.")
        @Size(max = 255, message = "이메일이 너무 길어요.")
        String email,

        @Schema(example = "warmwool123")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 64, message = "비밀번호는 64자 이하로 정해 주세요.")
        String password,

        @Schema(example = "다정한 목동")
        @NotNull(message = "닉네임을 입력해 주세요.")
        String nickname,

        @Schema(description = "이용약관과 'AI 케어는 전문 상담을 대신하지 않는다'는 안내 동의", example = "true")
        @AssertTrue(message = "이용약관과 안내에 동의해 주세요.")
        boolean agreed
) {
}
