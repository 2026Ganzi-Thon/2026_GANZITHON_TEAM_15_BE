package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.user.service.NicknameContentFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 닉네임에 전화번호·SNS 아이디 같은 개인정보가 들어 있으면 쓸 수 없게 합니다 (SAF-05). */
@Component
@RequiredArgsConstructor
class PiiNicknameContentFilter implements NicknameContentFilter {

    private final PiiDetector piiDetector;

    @Override
    public boolean isAllowed(String nickname) {
        return !piiDetector.containsPii(nickname);
    }
}
