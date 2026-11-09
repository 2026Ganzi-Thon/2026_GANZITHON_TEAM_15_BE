package com.ganzithon.teolhanol.domain.user.dto;

/** 로그인 결과. accessToken을 Authorization: Bearer 헤더에 담아 보냅니다. */
public record LoginResponse(Long userId, String nickname, String accessToken, long expiresInSeconds) {
}
