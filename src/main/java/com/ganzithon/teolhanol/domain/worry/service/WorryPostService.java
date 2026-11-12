package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.safety.dto.RiskResult;
import com.ganzithon.teolhanol.domain.safety.entity.CrisisSource;
import com.ganzithon.teolhanol.domain.safety.service.CrisisHelpInfo;
import com.ganzithon.teolhanol.domain.safety.service.PiiDetector;
import com.ganzithon.teolhanol.domain.safety.service.RiskClassifier;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.worry.dto.WorryPostResponse;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 고민 올리기 (FRM-04, SAF-01, SAF-05).
 * 순서: 목장 확인 → 길이 → 하루 한도 → 개인정보 → 위험도 분류 → 저장.
 * LLM 분류는 트랜잭션 밖에서 하고, 저장만 {@link WorryPostWriter}의 짧은 트랜잭션에서 합니다.
 * 고민 본문은 로그에 남기지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorryPostService {

    /** 목장 목록의 '전체' 탭 코드. 볼 때만 쓰고 올릴 때는 쓸 수 없습니다. */
    private static final String ALL_FARMS = "ALL";

    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final PiiDetector piiDetector;
    private final RiskClassifier riskClassifier;
    private final WorryPostWriter writer;
    private final PolicyProperties policy;

    public WorryPostResponse post(Long userId, String farmCode, String rawContent) {
        Farm farm = findFarm(farmCode);
        String content = validContent(rawContent);
        writer.checkDailyLimit(userId);

        if (piiDetector.containsPii(content)) {
            log.info("고민 올리기 개인정보 패턴으로 저장하지 않음 userId={}", userId);
            return WorryPostResponse.piiRejected(ErrorCode.PII_DETECTED.getMessage());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        RiskResult risk = riskClassifier.classify(content, CrisisSource.WORRY, user);
        if (risk.isCrisis()) {
            // 양을 만들지 않습니다. 상담 배너는 RiskClassifier가 켭니다.
            return WorryPostResponse.crisis(CrisisHelpInfo.INFO);
        }
        if (risk.llmFailed()) {
            // 분류 API 실패: 위기 키워드 1차 판정에서 위기가 아니면 RiskClassifier가 최소 '주의'로 돌려주므로 보호 양으로 등록합니다.
            log.warn("고민 위험도 LLM 분류 실패, 키워드 1차 판정 결과로 등록합니다 userId={} level={}", userId, risk.level());
        }

        RiskLevel level = risk.level() == RiskLevel.CAUTION ? RiskLevel.CAUTION : RiskLevel.SAFE;
        WorrySheep sheep = writer.register(userId, farm.getId(), content, level);
        log.info("고민 양 등록 sheepId={} farm={} level={}", sheep.getId(), farm.getCode(), level);
        return WorryPostResponse.created(sheep.getId(), farm,
                SheepStage.ofWorrySheep(sheep.getWoolCount()), sheep.getContent());
    }

    private Farm findFarm(String farmCode) {
        String code = farmCode == null ? "" : farmCode.strip().toUpperCase(Locale.ROOT);
        if (ALL_FARMS.equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "고민을 올릴 목장을 하나 골라 주세요.");
        }
        return farmRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "찾으시는 목장이 없어요."));
    }

    private String validContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        int max = policy.worry().maxLength();
        if (content.isEmpty() || content.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "고민은 1자에서 " + max + "자 사이로 적어 주세요.");
        }
        return content;
    }
}
