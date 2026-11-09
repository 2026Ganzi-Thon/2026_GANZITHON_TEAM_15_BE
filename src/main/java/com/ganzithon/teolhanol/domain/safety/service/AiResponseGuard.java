package com.ganzithon.teolhanol.domain.safety.service;

/**
 * AI 응답 안전 가드 (SAF-03). 케어 대화에서 AI 응답을 저장하기 전에 한 번 거칩니다.
 * 응답이 문제없으면 그대로, 문제가 있으면 대신 보낼 안전한 문장을 돌려줍니다.
 * 실제 판정(진단 단정, 약 조언 등 금지 패턴)은 #32에서 구현하며, 그때까지는 {@link PassThroughAiResponseGuard}가 그대로 통과시킵니다.
 */
public interface AiResponseGuard {

    /** @return 검사를 통과한 문장. 입력과 다르면 호출한 쪽이 대체 문장으로 교체해 저장합니다. */
    String guard(String response);
}
