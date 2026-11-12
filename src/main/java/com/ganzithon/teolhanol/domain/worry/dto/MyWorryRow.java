package com.ganzithon.teolhanol.domain.worry.dto;

import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import java.time.LocalDateTime;

/** 내 고민 목록 조회 결과 한 줄 (FRM-05). 저장소에서만 쓰는 내부 값입니다. */
public record MyWorryRow(Long sheepId, String farmCode, String farmName, String farmTabName, String content,
                         int woolCount, WorrySheepStatus status, LocalDateTime createdAt,
                         LocalDateTime graduatedAt, long newWoolCount) {
}
