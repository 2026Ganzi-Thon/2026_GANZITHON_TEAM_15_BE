package com.ganzithon.teolhanol.domain.wool.service;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.dto.WoolSendResponse;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.event.WoolArrivedEvent;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.domain.worry.service.WorrySheepAccess;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조언(털) 보내기의 권한 검사와 저장 (WOL-01). LLM 다시 확인이 끝난 뒤 짧은 트랜잭션으로 저장합니다.
 * 양 행 → 보낸 사람 행 순서로 잠근 뒤 상태·상한·중복을 다시 확인하므로, 동시에 보내도 털이 상한을 넘지 않고
 * 같은 사람이 두 번 보낼 수 없습니다. 조언 본문은 로그에 남기지 않습니다.
 */
@Component
@RequiredArgsConstructor
class WoolSendWriter {

    private final WorrySheepRepository worrySheepRepository;
    private final WoolRepository woolRepository;
    private final UserRepository userRepository;
    private final WorrySheepAccess access;
    private final ApplicationEventPublisher eventPublisher;
    private final PolicyProperties policy;

    /** 잠금 없이 보낼 수 있는 양인지 먼저 거릅니다. LLM을 부르기 전에 씁니다. */
    @Transactional(readOnly = true)
    public void checkSendable(Long senderId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findById(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        verify(sheep, sender);
    }

    @Transactional
    public WoolSendResponse send(Long senderId, Long sheepId, String content, boolean sentAsIs) {
        WorrySheep sheep = worrySheepRepository.findByIdForUpdate(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        User sender = userRepository.findByIdForUpdate(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        verify(sheep, sender);

        int cap = policy.worry().woolCap();
        SheepStage before = SheepStage.ofWorrySheep(sheep.getWoolCount());
        int yarnBefore = sender.getYarnCount();

        // flush 해서 UNIQUE(sheep_id, sender_id) 위반을 여기서 드러냅니다.
        Wool wool = woolRepository.saveAndFlush(new Wool(sheep, sender, content, sentAsIs));
        sheep.addWool(cap);
        sender.addSentWool();
        boolean graduated = sheep.getWoolCount() >= cap;
        if (graduated) {
            sheep.graduate();
        }
        SheepStage after = SheepStage.ofWorrySheep(sheep.getWoolCount());

        eventPublisher.publishEvent(new WoolArrivedEvent(sheep.getId(), sheep.getUser().getId(), wool.getId()));
        return WoolSendResponse.sent(wool.getId(), after, after.getLevel() > before.getLevel(), graduated,
                sender.getYarnCount() - yarnBefore, sender.getYarnCount());
    }

    /**
     * 볼 수 없는 양(숨김·차단 관계·이력이 모자란 보호 양)은 존재 여부를 흘리지 않도록 404로 답합니다 (#22 상세와 같은 기준).
     * 그다음 내 양 → 이미 보냄 → 졸업(또는 털 상한) 순서로 확인합니다.
     */
    private void verify(WorrySheep sheep, User sender) {
        Long senderId = sender.getId();
        if (access.isHidden(sheep)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (access.isOwner(sheep, senderId)) {
            throw new BusinessException(ErrorCode.WOOL_OWN_SHEEP);
        }
        if (access.isBlockedBetween(senderId, sheep.getUser().getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean alreadySent = access.hasSent(sheep.getId(), senderId);
        if (!access.canSeeRisk(sheep, sender, alreadySent)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (alreadySent) {
            throw new BusinessException(ErrorCode.WOOL_ALREADY_SENT);
        }
        if (sheep.getStatus() != WorrySheepStatus.ACTIVE || sheep.getWoolCount() >= policy.worry().woolCap()) {
            throw new BusinessException(ErrorCode.WORRY_SHEEP_GRADUATED);
        }
    }
}
