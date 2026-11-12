package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고민 양 저장과 하루 한도 검사 (FRM-04). LLM 분류가 끝난 뒤 짧은 트랜잭션으로 저장합니다.
 * 같은 사용자의 동시 요청이 한도를 넘지 않도록 사용자 행을 잠근 뒤 다시 세고 저장합니다.
 */
@Component
@RequiredArgsConstructor
class WorryPostWriter {

    /** '오늘'의 기준 시간대 */
    static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final WorrySheepRepository worrySheepRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final PolicyProperties policy;

    /** 오늘 만든 양(숨김·졸업 포함)이 한도에 닿았으면 429. 잠금 없이 빠르게 거르는 용도입니다. */
    @Transactional(readOnly = true)
    public void checkDailyLimit(Long userId) {
        if (worrySheepRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, startOfToday())
                >= policy.worry().dailyLimit()) {
            throw new BusinessException(ErrorCode.WORRY_DAILY_LIMIT_EXCEEDED);
        }
    }

    @Transactional
    public WorrySheep register(Long userId, Long farmId, String content, RiskLevel riskLevel) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        checkDailyLimit(userId);
        Farm farm = farmRepository.getReferenceById(farmId);
        return worrySheepRepository.save(new WorrySheep(user, farm, content, riskLevel));
    }

    /**
     * 서울 기준 오늘 0시를 서버 기본 시간대의 LocalDateTime으로 바꿉니다.
     * created_at은 서버 기본 시간대의 LocalDateTime.now()로 채워지므로 같은 기준으로 비교합니다.
     */
    private static LocalDateTime startOfToday() {
        return LocalDate.now(SERVICE_ZONE).atStartOfDay(SERVICE_ZONE)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
