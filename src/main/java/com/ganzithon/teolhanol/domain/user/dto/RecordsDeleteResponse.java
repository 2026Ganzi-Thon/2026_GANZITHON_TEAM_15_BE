package com.ganzithon.teolhanol.domain.user.dto;

/** 기록 전체 삭제 결과. 지운 오늘의 마음 수 (이미 쌓인 털·털실·아이템은 그대로) */
public record RecordsDeleteResponse(long deletedDiaries) {
}
