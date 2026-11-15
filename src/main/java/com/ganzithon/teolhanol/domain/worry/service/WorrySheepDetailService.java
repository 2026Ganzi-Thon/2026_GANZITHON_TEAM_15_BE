package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.dto.WorrySheepDetailResponse;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고민 양 상세 조회 (FRM-03, FRM-05). 볼 수 없는 경우는 존재 여부를 흘리지 않도록 모두 NOT_FOUND로 답합니다. */
@Service
@RequiredArgsConstructor
public class WorrySheepDetailService {

    private final WorrySheepRepository worrySheepRepository;
    private final WoolRepository woolRepository;
    private final ThanksRepository thanksRepository;
    private final WorrySheepAccess access;

    /** 주인이 조회하면 '확인한 시각'을 남기므로 쓰기 트랜잭션입니다. */
    @Transactional
    public WorrySheepDetailResponse get(Long viewerId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findDetailById(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        boolean mine = access.isOwner(sheep, viewerId);
        boolean alreadySent = !mine && !access.isHidden(sheep) && access.hasSent(sheepId, viewerId);
        if (!access.canView(sheep, viewerId, alreadySent)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        List<WorrySheepDetailResponse.WoolItem> wools = null;
        if (mine) {
            // 조회 직전 시각을 기록해, 조회 도중 도착한 털이 '새 털'에서 빠지지 않게 합니다.
            LocalDateTime checkedAt = LocalDateTime.now();
            wools = receivedWools(sheepId);
            sheep.markOwnerChecked(checkedAt);
        }

        SheepStage stage = SheepStage.ofWorrySheep(sheep.getWoolCount());
        boolean canSendWool = !mine && !alreadySent && sheep.getStatus() == WorrySheepStatus.ACTIVE;
        return new WorrySheepDetailResponse(
                sheep.getId(),
                new WorrySheepDetailResponse.Farm(sheep.getFarm().getCode(), sheep.getFarm().getName(),
                        sheep.getFarm().getTabName()),
                sheep.getContent(),
                stage.getLevel(),
                stage.getLabel(),
                sheep.getUser().getNickname(),
                sheep.getCreatedAt(),
                sheep.getStatus().name(),
                mine,
                canSendWool,
                alreadySent,
                wools);
    }

    /** 신고 누적으로 숨겨진 조언(RPT-01)은 빼고 보여줍니다. */
    private List<WorrySheepDetailResponse.WoolItem> receivedWools(Long sheepId) {
        List<Wool> wools = woolRepository.findAllVisibleWithSenderBySheepId(sheepId);
        Set<Long> thanked = new HashSet<>(thanksRepository.findThankedWoolIdsBySheepId(sheepId));
        return wools.stream()
                .map(w -> new WorrySheepDetailResponse.WoolItem(w.getId(), w.getContent(),
                        w.getSender().getNickname(), w.getCreatedAt(), thanked.contains(w.getId())))
                .toList();
    }
}
