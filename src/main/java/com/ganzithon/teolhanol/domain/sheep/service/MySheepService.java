package com.ganzithon.teolhanol.domain.sheep.service;

import com.ganzithon.teolhanol.domain.sheep.dto.MySheepResponse;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheepItem;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepItemRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 나의 양 조회 (SHP-01). 다른 사람과 비교하는 정보는 만들지 않습니다. */
@Service
@RequiredArgsConstructor
public class MySheepService {

    private final MySheepRepository mySheepRepository;
    private final MySheepItemRepository mySheepItemRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MySheepResponse get(Long userId) {
        MySheep sheep = mySheepRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        SheepStage stage = SheepStage.ofMySheep(sheep.getWoolCount());
        SheepStage next = stage.next();
        Integer nextStageAt = next == null ? null : next.getMyWoolThreshold();
        int woolToNext = next == null ? 0 : next.getMyWoolThreshold() - sheep.getWoolCount();

        var items = mySheepItemRepository.findByMySheepIdAndEquippedTrueOrderById(sheep.getId()).stream()
                .map(MySheepItem::getItemCode).toList();
        return new MySheepResponse(stage.getLevel(), stage.getLabel(), sheep.getWoolCount(),
                stage.getMyWoolThreshold(), nextStageAt, woolToNext, user.getYarnCount(), items);
    }
}
