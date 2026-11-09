package com.ganzithon.teolhanol.domain.user.service;

/**
 * 닉네임 내용 검사 확장 지점. 금칙어(#33)·개인정보 패턴(#34) 검사기가 이 인터페이스를 구현해 빈으로 등록하면
 * 닉네임 검사에 자동으로 연결됩니다. 하나라도 false면 NOT_ALLOWED입니다.
 */
public interface NicknameContentFilter {

    boolean isAllowed(String nickname);
}
