package com.ganzithon.teolhanol.global.llm;

/** 대화 한 턴. 이메일·닉네임 같은 사용자 식별 정보는 넣지 않습니다 (기능명세서 6장 AI 데이터). */
public record LlmMessage(Role role, String content) {

    public enum Role {
        USER, ASSISTANT
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(Role.ASSISTANT, content);
    }
}
