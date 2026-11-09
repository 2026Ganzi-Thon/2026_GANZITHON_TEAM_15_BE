package com.ganzithon.teolhanol.domain.user.dto;

/** 가입 결과. 로그인 토큰은 #7(ACC-02)에서 함께 내려줍니다. */
public record SignupResponse(Long userId, String nickname, int sheepStage, int sheepWoolCount) {
}
