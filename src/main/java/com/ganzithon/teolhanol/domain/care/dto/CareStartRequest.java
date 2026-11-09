package com.ganzithon.teolhanol.domain.care.dto;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 케어 방식 고르기 요청 (CARE-01) */
public record CareStartRequest(
        @Schema(description = "LISTEN 이야기 들어주기 / COMFORT 위로받기 / ORGANIZE 생각 정리하기 / SOLVE 해결 방법 찾기", example = "COMFORT")
        @NotNull(message = "케어 방식을 골라 주세요.")
        CareType careType
) {
}
