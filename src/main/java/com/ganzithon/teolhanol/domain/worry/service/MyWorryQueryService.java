package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.worry.dto.MyWorryListResponse;
import com.ganzithon.teolhanol.domain.worry.dto.MyWorryRow;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 고민 목록 (FRM-05 진행 중, FRM-06 보관함). 본인 양만 돌려줍니다. */
@Service
@RequiredArgsConstructor
public class MyWorryQueryService {

    private static final String ACTIVE = "ACTIVE";
    private static final String GRADUATED = "GRADUATED";

    private final WorrySheepRepository worrySheepRepository;

    /** @param status active | graduated (대소문자 무시). 비어 있으면 active, 그 밖의 값은 INVALID_INPUT */
    @Transactional(readOnly = true)
    public MyWorryListResponse list(Long userId, String status) {
        String tab = status == null || status.isBlank() ? ACTIVE : status.trim().toUpperCase(Locale.ROOT);
        // 진행 중 탭에는 신고로 숨겨진 내 양도 함께 보여 주고 hidden=true로 표시합니다.
        List<WorrySheepStatus> statuses = switch (tab) {
            case ACTIVE -> List.of(WorrySheepStatus.ACTIVE, WorrySheepStatus.HIDDEN);
            case GRADUATED -> List.of(WorrySheepStatus.GRADUATED);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
        var items = worrySheepRepository.findMine(userId, statuses).stream().map(this::toItem).toList();
        return new MyWorryListResponse(tab, items);
    }

    private MyWorryListResponse.Item toItem(MyWorryRow row) {
        SheepStage stage = SheepStage.ofWorrySheep(row.woolCount());
        return new MyWorryListResponse.Item(
                row.sheepId(),
                new MyWorryListResponse.Farm(row.farmCode(), row.farmName(), row.farmTabName()),
                WorryPreview.of(row.content()),
                stage.getLevel(),
                stage.getLabel(),
                row.createdAt(),
                row.graduatedAt(),
                row.newWoolCount(),
                row.status() == WorrySheepStatus.HIDDEN);
    }
}
