package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.user.service.NicknameContentFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 금칙어가 들어간 닉네임을 막습니다 (SAF-04, 기능명세서 5장 닉네임 규칙). 가입·사용 가능 확인·닉네임 바꾸기에 함께 적용됩니다. */
@Component
@RequiredArgsConstructor
class BannedWordNicknameFilter implements NicknameContentFilter {

    private final BannedWordFilter bannedWordFilter;

    @Override
    public boolean isAllowed(String nickname) {
        return !bannedWordFilter.contains(nickname);
    }
}
