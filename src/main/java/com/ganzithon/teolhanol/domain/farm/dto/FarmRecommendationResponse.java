package com.ganzithon.teolhanol.domain.farm.dto;

/** 케어 마무리에서 추천하는 목장 (FRM-07). 대화 내용은 담지 않습니다. */
public record FarmRecommendationResponse(String farmCode, String name, String tabName) {
}
