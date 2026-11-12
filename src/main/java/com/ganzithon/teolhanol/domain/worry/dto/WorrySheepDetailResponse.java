package com.ganzithon.teolhanol.domain.worry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 고민 양 상세 (FRM-03, FRM-05). 털 개수 숫자는 담지 않고 단계만 보여줍니다.
 * wools는 고민 주인에게만 담기고, 다른 사람에게는 필드 자체가 없습니다.
 */
public record WorrySheepDetailResponse(
        Long sheepId,
        Farm farm,
        String content,
        int stage,
        String stageName,
        String nickname,
        LocalDateTime createdAt,
        String status,
        boolean mine,
        boolean canSendWool,
        boolean alreadySent,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<WoolItem> wools
) {

    public record Farm(String code, String name, String tabName) {
    }

    public record WoolItem(Long woolId, String content, String senderNickname, LocalDateTime createdAt,
                           boolean thanked) {
    }
}
