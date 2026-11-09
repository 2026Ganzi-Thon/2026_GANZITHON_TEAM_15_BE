package com.ganzithon.teolhanol.global.security;

/**
 * 인증된 현재 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser user} 로 받아 {@code user.id()} 를 씁니다.
 */
public record AuthUser(Long id) {
}
