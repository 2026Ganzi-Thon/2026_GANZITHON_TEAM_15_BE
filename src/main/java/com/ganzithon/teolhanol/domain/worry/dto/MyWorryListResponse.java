package com.ganzithon.teolhanol.domain.worry.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 고민 목록 (FRM-05, FRM-06). 털 개수 숫자 없이 단계만 담고, 새 털 수(newWoolCount)만 알려 줍니다.
 *
 * @param status ACTIVE(진행 중) 또는 GRADUATED(보관함)
 */
public record MyWorryListResponse(String status, List<Item> sheep) {

    public record Farm(String code, String name, String tabName) {
    }

    /** @param hidden 신고로 숨겨진 내 양인지 (진행 중 탭에만 나타납니다) */
    public record Item(Long sheepId, Farm farm, String preview, int stage, String stageName,
                       LocalDateTime createdAt, LocalDateTime graduatedAt, long newWoolCount, boolean hidden) {
    }
}
