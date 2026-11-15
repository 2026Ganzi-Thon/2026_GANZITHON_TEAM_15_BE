package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.worry.dto.WorryGraduateResponse;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고민 양 졸업 (FRM-06). 졸업 경로는 세 가지입니다.
 * 주인의 직접 졸업(여기), 털 상한 도달(WoolSendWriter 가 조언 저장 트랜잭션에서 처리), 작성 후 N일 경과(여기, 스케줄러).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorryGraduationService {

    private final WorrySheepRepository worrySheepRepository;
    private final PolicyProperties policy;

    /** 주인이 직접 졸업시킵니다. 주인이 아니거나 없는·숨김 양은 404, 이미 졸업했으면 현재 상태 그대로(멱등). */
    @Transactional
    public WorryGraduateResponse graduate(Long ownerId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findByIdForUpdate(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!sheep.getUser().getId().equals(ownerId) || sheep.getStatus() == WorrySheepStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (sheep.getStatus() == WorrySheepStatus.ACTIVE) {
            sheep.graduate();
        }
        SheepStage stage = SheepStage.ofWorrySheep(sheep.getWoolCount());
        return new WorryGraduateResponse(sheep.getId(), sheep.getStatus().name(), sheep.getGraduatedAt(),
                stage.getLevel(), stage.getLabel());
    }

    /** 작성 후 정책 일수가 지난 ACTIVE 양을 한 번의 UPDATE 로 졸업시킵니다. 졸업시킨 수를 돌려줍니다. */
    @Transactional
    public int graduateExpired(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(policy.worry().autoGraduateDays());
        int count = worrySheepRepository.graduateCreatedBefore(threshold, now);
        if (count > 0) {
            log.info("기간 경과 고민 양 자동 졸업 count={}", count);
        }
        return count;
    }
}
