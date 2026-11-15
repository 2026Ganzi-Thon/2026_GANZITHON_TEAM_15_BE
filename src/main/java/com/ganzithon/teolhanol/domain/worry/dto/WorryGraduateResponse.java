package com.ganzithon.teolhanol.domain.worry.dto;

import java.time.LocalDateTime;

/** 고민 양 졸업 결과 (FRM-06). 이미 졸업한 양이면 현재 상태를 그대로 돌려줍니다. */
public record WorryGraduateResponse(Long sheepId, String status, LocalDateTime graduatedAt, int stage,
                                    String stageName) {
}
