package com.ganzithon.teolhanol.domain.sheep.dto;

import java.util.List;

/**
 * 나의 양 (HOME-01 진행 바용).
 *
 * @param stage          1~5단계
 * @param stageName      예: "포근한 양"
 * @param woolCount      누적 털
 * @param stageStartAt   현재 단계가 시작된 누적 털 (진행 바 왼쪽 끝)
 * @param nextStageAt    다음 단계가 되는 누적 털 (진행 바 오른쪽 끝). 마지막 단계면 null
 * @param woolToNextStage 다음 단계까지 남은 털. 마지막 단계면 0
 * @param yarnCount      가진 털실
 * @param equippedItems  착용 중인 아이템 코드 (꾸미기 전에는 빈 목록)
 */
public record MySheepResponse(int stage, String stageName, int woolCount, int stageStartAt, Integer nextStageAt,
                              int woolToNextStage, int yarnCount, List<String> equippedItems) {
}
