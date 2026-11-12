package com.ganzithon.teolhanol.domain.safety.dto;

import java.time.LocalDateTime;

/** 24시간 상담 배너 상태. 꺼져 있으면 crisisBannerUntil 은 null 입니다. */
public record CrisisBannerResponse(boolean active, LocalDateTime crisisBannerUntil) {
}
