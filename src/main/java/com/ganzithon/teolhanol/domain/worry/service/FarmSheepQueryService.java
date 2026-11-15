package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.worry.dto.FarmSheepPageResponse;
import com.ganzithon.teolhanol.domain.worry.dto.FarmSheepRow;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고민 목장 풀밭의 양 목록 (FRM-01 FARM-03).
 * 털 적은 양이 먼저 보이도록 정렬하고, 차단 관계·보호 양 노출 조건은 요청자 기준으로 DB에서 거릅니다.
 */
@Service
@RequiredArgsConstructor
public class FarmSheepQueryService {

    /** 전체 탭을 뜻하는 farmCode */
    public static final String ALL = "ALL";
    private static final int PREVIEW_LENGTH = 40;
    private static final String ELLIPSIS = "…";

    private final WorrySheepRepository worrySheepRepository;
    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final PolicyProperties policy;

    @Transactional(readOnly = true)
    public FarmSheepPageResponse list(Long viewerId, String farmCode, int page) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String code = farmCode.trim().toUpperCase(Locale.ROOT);
        Long farmId = ALL.equals(code) ? null : farmRepository.findByCode(code)
                .map(Farm::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        int size = policy.worry().pageSize();
        Slice<FarmSheepRow> rows = worrySheepRepository.findFarmSheep(
                viewerId, farmId, visibleRiskLevels(viewer), PageRequest.of(page, size));

        var items = rows.getContent().stream().map(this::toItem).toList();
        return new FarmSheepPageResponse(code, page, size, rows.hasNext(), items);
    }

    /** '주의'(보호) 양은 조언을 충분히 보낸 사용자에게만 보입니다. 위기 고민은 목장에 올라오지 않지만 혹시 있어도 보이지 않습니다. */
    private Set<RiskLevel> visibleRiskLevels(User viewer) {
        if (viewer.getSentWoolCount() >= policy.worry().protectedViewMinSentWools()) {
            return EnumSet.of(RiskLevel.SAFE, RiskLevel.CAUTION);
        }
        return EnumSet.of(RiskLevel.SAFE);
    }

    private FarmSheepPageResponse.Item toItem(FarmSheepRow row) {
        SheepStage stage = SheepStage.ofWorrySheep(row.woolCount());
        return new FarmSheepPageResponse.Item(row.sheepId(), row.farmCode(), stage.getLevel(), stage.getLabel(),
                preview(row.content()), row.nickname(), row.createdAt(), row.alreadySent());
    }

    /** 고민 앞부분 40자. 넘으면 "…"를 붙입니다. 이모지가 반으로 잘리지 않도록 코드 포인트 기준으로 자릅니다. */
    private String preview(String content) {
        String text = content.strip();
        if (text.codePointCount(0, text.length()) <= PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH)) + ELLIPSIS;
    }
}
