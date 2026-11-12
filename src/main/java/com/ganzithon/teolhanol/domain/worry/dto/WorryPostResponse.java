package com.ganzithon.teolhanol.domain.worry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.safety.dto.CrisisHelpResponse;
import com.ganzithon.teolhanol.global.policy.SheepStage;

/**
 * 고민 올리기 결과 (FRM-04). status 에 따라 채워지는 값이 다릅니다.
 * <ul>
 *     <li>CREATED: sheepId, farm, stage, stageName, content (FARM-02 '새 양이 태어났어요' + 고민 인용)</li>
 *     <li>CRISIS: crisisHelp (양을 만들지 않고 상담 연결 화면, SAF-02)</li>
 *     <li>PII_REJECTED: message (저장하지 않고 수정 요청, SAF-05)</li>
 * </ul>
 * 보호 양(주의) 여부와 털 개수 숫자는 내려주지 않습니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorryPostResponse(
        WorryPostStatus status,
        Long sheepId,
        FarmInfo farm,
        Integer stage,
        String stageName,
        String content,
        CrisisHelpResponse crisisHelp,
        String message
) {

    public record FarmInfo(String code, String name, String tabName) {

        public static FarmInfo of(Farm farm) {
            return new FarmInfo(farm.getCode(), farm.getName(), farm.getTabName());
        }
    }

    public static WorryPostResponse created(Long sheepId, Farm farm, SheepStage stage, String content) {
        return new WorryPostResponse(WorryPostStatus.CREATED, sheepId, FarmInfo.of(farm),
                stage.getLevel(), stage.getLabel(), content, null, null);
    }

    public static WorryPostResponse crisis(CrisisHelpResponse crisisHelp) {
        return new WorryPostResponse(WorryPostStatus.CRISIS, null, null, null, null, null, crisisHelp, null);
    }

    public static WorryPostResponse piiRejected(String message) {
        return new WorryPostResponse(WorryPostStatus.PII_REJECTED, null, null, null, null, null, null, message);
    }
}
