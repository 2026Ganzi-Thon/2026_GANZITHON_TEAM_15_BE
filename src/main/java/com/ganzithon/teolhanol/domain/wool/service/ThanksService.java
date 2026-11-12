package com.ganzithon.teolhanol.domain.wool.service;

import com.ganzithon.teolhanol.domain.wool.dto.ThanksResponse;
import com.ganzithon.teolhanol.domain.wool.entity.Thanks;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.event.WoolThankedEvent;
import com.ganzithon.teolhanol.domain.wool.repository.ThanksRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고마워요 (WOL-03). 그 조언이 달린 양의 주인만, 조언당 한 번 누를 수 있습니다.
 * 보상(털실 등)은 없습니다. 졸업한 양(보관함)의 조언에도 누를 수 있습니다.
 */
@Service
@RequiredArgsConstructor
public class ThanksService {

    private final WoolRepository woolRepository;
    private final WorrySheepRepository worrySheepRepository;
    private final ThanksRepository thanksRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ThanksResponse thank(Long userId, Long woolId) {
        Wool wool = woolRepository.findById(woolId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 같은 양의 고마워요 요청을 한 줄로 세워, 동시에 눌러도 한 번만 저장합니다.
        WorrySheep sheep = worrySheepRepository.findByIdForUpdate(wool.getSheep().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 주인이 아니거나 숨긴 양이면 조언이 있는지 알 수 없도록 404로 답합니다.
        if (!sheep.getUser().getId().equals(userId) || sheep.getStatus() == WorrySheepStatus.HIDDEN
                || wool.isHidden()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (thanksRepository.existsByWoolId(woolId)) {
            throw new BusinessException(ErrorCode.THANKS_ALREADY_SENT);
        }
        try {
            thanksRepository.saveAndFlush(new Thanks(wool));
        } catch (DataIntegrityViolationException e) {
            // thanks.wool_id UNIQUE 위반
            throw new BusinessException(ErrorCode.THANKS_ALREADY_SENT);
        }
        eventPublisher.publishEvent(new WoolThankedEvent(woolId, sheep.getId(), wool.getSender().getId()));
        return new ThanksResponse(woolId, true);
    }
}
