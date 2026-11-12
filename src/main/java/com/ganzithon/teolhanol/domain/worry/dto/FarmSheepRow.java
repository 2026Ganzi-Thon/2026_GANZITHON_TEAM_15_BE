package com.ganzithon.teolhanol.domain.worry.dto;

import java.time.LocalDateTime;

/**
 * 풀밭 목록 조회 결과 한 줄 (FRM-01). 저장소에서만 쓰는 내부 값이며 응답으로 그대로 내보내지 않습니다.
 *
 * @param alreadySent 요청자가 이미 이 양에 조언(털)을 보냈는지
 */
public record FarmSheepRow(Long sheepId, String farmCode, int woolCount, String content,
                           String nickname, LocalDateTime createdAt, boolean alreadySent) {
}
