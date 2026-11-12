package com.ganzithon.teolhanol.domain.worry.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고민 목장 풀밭의 양 목록 한 페이지 (FRM-01 FARM-03).
 *
 * @param farmCode 조회한 탭. 전체 탭이면 "ALL"
 * @param page     0부터 시작하는 페이지 번호
 * @param size     한 페이지 크기
 * @param hasNext  다음 페이지가 있는지
 * @param sheep    털 적은 순 → 오래된 순. 이미 조언을 보낸 양은 뒤로
 */
public record FarmSheepPageResponse(String farmCode, int page, int size, boolean hasNext, List<Item> sheep) {

    /**
     * 풀밭의 양 한 마리. 양을 누르면 아래 카드에 보여줄 정보입니다.
     * 작성자는 닉네임만 내려주고, 인기·조회수·털 개수는 내려주지 않습니다.
     *
     * @param stage       단계 (1~5, 받은 조언 수 기준)
     * @param stageName   단계 이름 (예: 털 깎은 양)
     * @param preview     고민 앞부분 (40자, 넘으면 "…")
     * @param nickname    작성자 닉네임
     * @param createdAt   작성 시각. 상대 시간 문구(예: 3시간 전)는 프론트에서 만듭니다
     * @param alreadySent 내가 이미 조언을 보낸 양인지
     */
    public record Item(Long sheepId, String farmCode, int stage, String stageName, String preview,
                       String nickname, LocalDateTime createdAt, boolean alreadySent) {
    }
}
