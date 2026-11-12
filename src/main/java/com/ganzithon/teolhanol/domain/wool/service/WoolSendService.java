package com.ganzithon.teolhanol.domain.wool.service;

import com.ganzithon.teolhanol.domain.safety.dto.AdviceCategory;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckResult;
import com.ganzithon.teolhanol.domain.safety.dto.AdviceCheckStatus;
import com.ganzithon.teolhanol.domain.safety.service.AdviceChecker;
import com.ganzithon.teolhanol.domain.safety.service.PiiDetector;
import com.ganzithon.teolhanol.domain.wool.dto.WoolSendResponse;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 조언(털) 보내기 (WOL-01, SAF-04, SAF-05).
 * 순서: 길이 → 양 조회·권한 → 개인정보 → 조언 다시 확인(금칙어 + LLM 어조) → 저장.
 * LLM 확인은 트랜잭션 밖에서 하고, 저장만 {@link WoolSendWriter}의 짧은 트랜잭션에서 합니다.
 * 조언 본문과 바꿀 문장은 로그에 남기지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WoolSendService {

    private final WoolSendWriter writer;
    private final WoolRepository woolRepository;
    private final PiiDetector piiDetector;
    private final AdviceChecker adviceChecker;
    private final PolicyProperties policy;

    public WoolSendResponse send(Long senderId, Long sheepId, String rawContent, Boolean sendAsIs) {
        String content = validContent(rawContent);
        writer.checkSendable(senderId, sheepId);

        if (piiDetector.containsPii(content)) {
            log.info("조언 보내기 개인정보 패턴으로 저장하지 않음 sheepId={} senderId={}", sheepId, senderId);
            return WoolSendResponse.blocked(WoolSendResponse.PII_CATEGORY, ErrorCode.PII_DETECTED.getMessage());
        }

        AdviceCheckResult check = adviceChecker.check(content);
        if (check.status() == AdviceCheckStatus.BLOCKED) {
            log.info("조언 보내기 막음 sheepId={} senderId={} category={}", sheepId, senderId, check.category());
            return WoolSendResponse.blocked(check.category().name(), check.category().getMessage());
        }
        boolean asIs = Boolean.TRUE.equals(sendAsIs);
        if (check.status() == AdviceCheckStatus.SUGGEST && !asIs) {
            AdviceCategory category = check.category();
            return WoolSendResponse.suggest(category.name(), category.getLabel(), category.getMessage(),
                    check.suggestion());
        }
        // PASS 는 sendAsIs 를 보내 왔어도 그대로 보낸 조언이 아닙니다.
        boolean sentAsIs = check.status() == AdviceCheckStatus.SUGGEST;

        WoolSendResponse response;
        try {
            response = writer.send(senderId, sheepId, content, sentAsIs);
        } catch (DataIntegrityViolationException e) {
            if (woolRepository.existsBySheepIdAndSenderId(sheepId, senderId)) {
                throw new BusinessException(ErrorCode.WOOL_ALREADY_SENT);
            }
            throw e;
        }
        log.info("조언 보냄 sheepId={} woolId={} sentAsIs={} graduated={}",
                sheepId, response.woolId(), sentAsIs, response.graduated());
        return response;
    }

    private String validContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        int max = policy.wool().maxLength();
        if (content.isEmpty() || content.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조언은 1자에서 " + max + "자 사이로 적어 주세요.");
        }
        return content;
    }
}
