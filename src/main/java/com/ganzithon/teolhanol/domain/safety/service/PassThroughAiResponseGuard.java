package com.ganzithon.teolhanol.domain.safety.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** #32 구현 전까지 쓰는 임시 가드: 아무것도 막지 않습니다. 실제 가드 빈이 생기면 자동으로 빠집니다. */
@Component
@ConditionalOnMissingBean(value = AiResponseGuard.class, ignored = PassThroughAiResponseGuard.class)
class PassThroughAiResponseGuard implements AiResponseGuard {

    @Override
    public String guard(String response) {
        return response;
    }
}
