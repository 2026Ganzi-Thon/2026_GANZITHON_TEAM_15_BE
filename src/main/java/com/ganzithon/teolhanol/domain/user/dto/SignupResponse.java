package com.ganzithon.teolhanol.domain.user.dto;

/** 가입 결과. 가입 직후 바로 쓸 수 있는 액세스 토큰을 함께 내려줍니다 (Authorization: Bearer). */
public record SignupResponse(Long userId, String nickname, int sheepStage, int sheepWoolCount,
                             String accessToken, long expiresInSeconds) {
}
