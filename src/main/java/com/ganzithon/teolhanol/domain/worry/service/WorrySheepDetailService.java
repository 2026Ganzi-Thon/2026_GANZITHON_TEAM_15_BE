package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.dto.WorrySheepDetailResponse;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.policy.SheepStage;
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
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final PolicyProperties policy;

    @Transactional(readOnly = true)
    public WorrySheepDetailResponse get(Long viewerId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findDetailById(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Long ownerId = sheep.getUser().getId();
        boolean mine = ownerId.equals(viewerId);

        if (sheep.getStatus() == WorrySheepStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean alreadySent = !mine && woolRepository.existsBySheepIdAndSenderId(sheepId, viewerId);
        if (!mine) {
            if (sheep.getStatus() == WorrySheepStatus.GRADUATED
                    || blockRepository.existsBetween(viewerId, ownerId)
                    || (sheep.getRiskLevel() == RiskLevel.CAUTION && !alreadySent && !hasEnoughHistory(viewerId))) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }

        List<WorrySheepDetailResponse.WoolItem> wools = null;
        if (mine) {
            // TODO(#24): 주인 확인 시각 기록
            wools = receivedWools(sheepId);
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

    private boolean hasEnoughHistory(Long viewerId) {
        return userRepository.findById(viewerId)
                .map(User::getSentWoolCount)
                .orElse(0) >= policy.worry().protectedViewMinSentWools();
    }

    private List<WorrySheepDetailResponse.WoolItem> receivedWools(Long sheepId) {
        List<Wool> wools = woolRepository.findAllWithSenderBySheepId(sheepId);
        Set<Long> thanked = new HashSet<>(thanksRepository.findThankedWoolIdsBySheepId(sheepId));
        return wools.stream()
                .map(w -> new WorrySheepDetailResponse.WoolItem(w.getId(), w.getContent(),
                        w.getSender().getNickname(), w.getCreatedAt(), thanked.contains(w.getId())))
                .toList();
    }
}
