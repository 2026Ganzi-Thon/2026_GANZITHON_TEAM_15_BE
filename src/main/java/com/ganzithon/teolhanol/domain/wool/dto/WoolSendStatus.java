package com.ganzithon.teolhanol.domain.wool.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 조언(털) 보내기 결과 (WOL-01) */
@Getter
@RequiredArgsConstructor
public enum WoolSendStatus {

    SENT("저장하고 털 한 올을 보냄"),
    SUGGEST("어조 주의. 저장하지 않고 바꿀 문장을 제안"),
    BLOCKED("보낼 수 없음. 저장하지 않고 다시 쓰기 요청");

    private final String label;
}
