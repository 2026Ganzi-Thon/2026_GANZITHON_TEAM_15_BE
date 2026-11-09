package com.ganzithon.teolhanol.domain.care.dto;

import java.util.List;

/** SSE 이벤트 본문 모음 (이벤트 이름은 각 record 위 주석) */
public final class CareEvent {

    private CareEvent() {
    }

    /** event: delta. AI 응답 조각 */
    public record Delta(String text) {
    }

    /** event: progress. 사용자 메시지를 받은 직후 한 번. ready는 털 한 올을 쌓을 수 있는지 */
    public record Progress(int userMessageCount, boolean ready, int maxUserMessages, boolean limitReached) {
    }

    /** event: crisis. AI 응답 대신 전환 메시지. actions: call(상담 전화 보기), continue(계속 이야기하기) */
    public record Crisis(String message, List<String> actions) {
    }

    /** event: notice. 응답이 늦어져 다시 시도하는 중 */
    public record Notice(String message) {
    }

    /** event: replace. 안전 가드가 응답을 대체함. 화면의 AI 말풍선을 이 문장으로 바꿈 */
    public record Replace(String text) {
    }

    /** event: error. 재시도에도 실패. 사용자 말은 저장되어 있어 다시 보내면 이어서 대화할 수 있음 */
    public record Error(String code, String message) {
    }
}
