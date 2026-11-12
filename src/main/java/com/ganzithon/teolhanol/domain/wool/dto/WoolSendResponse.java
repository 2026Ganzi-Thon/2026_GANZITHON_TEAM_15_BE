package com.ganzithon.teolhanol.domain.wool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ganzithon.teolhanol.global.policy.SheepStage;

/**
 * 조언(털) 보내기 결과 (WOL-01, SAF-04, SAF-05). status 에 따라 채워지는 값이 다릅니다.
 * <ul>
 *     <li>SENT: woolId, sheepStage, sheepStageName, stageUp, graduated, yarnAdded, yarnCount (FARM-04c)</li>
 *     <li>SUGGEST: category, categoryLabel, message, suggestion (FARM-04b, 저장하지 않음. 바꿀 문장이 없으면 suggestion 이 빠짐)</li>
 *     <li>BLOCKED: category, message (저장하지 않음. 개인정보면 category 가 PII)</li>
 * </ul>
 * 털 개수 숫자와 다른 사용자의 식별 정보는 내려주지 않습니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WoolSendResponse(
        WoolSendStatus status,
        Long woolId,
        Integer sheepStage,
        String sheepStageName,
        Boolean stageUp,
        Boolean graduated,
        Integer yarnAdded,
        Integer yarnCount,
        String category,
        String categoryLabel,
        String message,
        String suggestion
) {

    /** 개인정보 패턴(SAF-05)으로 막혔을 때의 category 값 */
    public static final String PII_CATEGORY = "PII";

    public static WoolSendResponse sent(Long woolId, SheepStage stage, boolean stageUp, boolean graduated,
                                        int yarnAdded, int yarnCount) {
        return new WoolSendResponse(WoolSendStatus.SENT, woolId, stage.getLevel(), stage.getLabel(), stageUp,
                graduated, yarnAdded, yarnCount, null, null, null, null);
    }

    public static WoolSendResponse suggest(String category, String categoryLabel, String message, String suggestion) {
        return new WoolSendResponse(WoolSendStatus.SUGGEST, null, null, null, null, null, null, null,
                category, categoryLabel, message, suggestion);
    }

    public static WoolSendResponse blocked(String category, String message) {
        return new WoolSendResponse(WoolSendStatus.BLOCKED, null, null, null, null, null, null, null,
                category, null, message, null);
    }
}
