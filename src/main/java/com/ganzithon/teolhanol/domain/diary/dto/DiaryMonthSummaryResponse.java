package com.ganzithon.teolhanol.domain.diary.dto;

/**
 * 이번 달 한 줄 돌아보기 (REV-02).
 *
 * @param month     조회한 달 (yyyy-MM)
 * @param woolCount 그 달에 마무리해 털 한 올을 받은 마음의 수 (위기 신호가 나온 대화는 제외)
 * @param sentence  예: '불안한 날엔 위로받기를 자주 골랐어요'. 기록이 3개 미만이면 null
 */
public record DiaryMonthSummaryResponse(String month, int woolCount, String sentence) {
}
