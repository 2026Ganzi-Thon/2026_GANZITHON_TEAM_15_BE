package com.ganzithon.teolhanol.domain.safety.dto;

/** 조언 다시 확인 결과 (SAF-04) */
public enum AdviceCheckStatus {

    /** 그대로 보내도 됨 */
    PASS,
    /** 어조 주의. 바꿀 문장을 제안하고 '그대로 보내기'도 허용 (FARM-04b) */
    SUGGEST,
    /** 보낼 수 없음. 다시 쓰기 요청 */
    BLOCKED
}
