package com.ganzithon.teolhanol.domain.farm.dto;

/**
 * 고민 목장 주제 탭 한 칸 (FRM-01).
 *
 * @param waitingCount 털을 기다리는 양 수 (목장에 있는 ACTIVE 고민 양, 숨김·졸업 제외)
 */
public record FarmResponse(String code, String name, String tabName, String description, long waitingCount) {
}
