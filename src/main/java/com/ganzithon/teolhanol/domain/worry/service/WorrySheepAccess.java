package com.ganzithon.teolhanol.domain.worry.service;

import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 고민 양을 '볼 수 있는가'의 판단을 한 곳에 모읍니다 (#22 상세 기준).
 * 상세(FRM-03)·조언 보내기(WOL-01)·차단(RPT-02)·신고(RPT-01)가 같은 기준을 씁니다.
 * 볼 수 없는 양은 존재 여부를 흘리지 않도록 호출하는 쪽에서 404로 답합니다.
 *
 * <ul>
 *   <li>숨김(HIDDEN) 양은 주인을 포함해 아무도 볼 수 없습니다.</li>
 *   <li>주인은 나머지 상태(진행·졸업)의 자기 양을 볼 수 있습니다.</li>
 *   <li>다른 목동에게는 졸업 양, 차단 관계(양방향) 사용자의 양, 볼 수 없는 보호 양이 보이지 않습니다.</li>
 *   <li>보호(CAUTION) 양은 이미 조언을 보냈거나 조언 이력이 충분한 목동에게만 보입니다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WorrySheepAccess {

    private final BlockRepository blockRepository;
    private final WoolRepository woolRepository;
    private final UserRepository userRepository;
    private final PolicyProperties policy;

    /** 상세와 같은 기준으로 이 사용자가 양을 볼 수 있는지 */
    public boolean canView(WorrySheep sheep, Long viewerId) {
        boolean mine = isOwner(sheep, viewerId);
        return canView(sheep, viewerId, !mine && hasSent(sheep.getId(), viewerId));
    }

    /** {@link #canView(WorrySheep, Long)} 와 같고, 이미 조회한 '조언을 보냈는지'를 재사용합니다. */
    public boolean canView(WorrySheep sheep, Long viewerId, boolean alreadySent) {
        if (isHidden(sheep)) {
            return false;
        }
        if (isOwner(sheep, viewerId)) {
            return true;
        }
        return sheep.getStatus() != WorrySheepStatus.GRADUATED
                && !isBlockedBetween(viewerId, sheep.getUser().getId())
                && canSeeRisk(sheep, viewerId, alreadySent);
    }

    public boolean isHidden(WorrySheep sheep) {
        return sheep.getStatus() == WorrySheepStatus.HIDDEN;
    }

    public boolean isOwner(WorrySheep sheep, Long userId) {
        return sheep.getUser().getId().equals(userId);
    }

    public boolean isBlockedBetween(Long a, Long b) {
        return blockRepository.existsBetween(a, b);
    }

    public boolean hasSent(Long sheepId, Long senderId) {
        return woolRepository.existsBySheepIdAndSenderId(sheepId, senderId);
    }

    /** 보호 양 노출 조건. 보낸 조언 수는 저장된 사용자 행에서 읽습니다. */
    public boolean canSeeRisk(WorrySheep sheep, Long viewerId, boolean alreadySent) {
        if (sheep.getRiskLevel() != RiskLevel.CAUTION || alreadySent) {
            return true;
        }
        int sent = userRepository.findById(viewerId).map(User::getSentWoolCount).orElse(0);
        return hasEnoughHistory(sent);
    }

    /** 보호 양 노출 조건. 잠금으로 이미 읽은 사용자 행을 그대로 씁니다(조언 보내기). */
    public boolean canSeeRisk(WorrySheep sheep, User viewer, boolean alreadySent) {
        return sheep.getRiskLevel() != RiskLevel.CAUTION || alreadySent
                || hasEnoughHistory(viewer.getSentWoolCount());
    }

    private boolean hasEnoughHistory(int sentWoolCount) {
        return sentWoolCount >= policy.worry().protectedViewMinSentWools();
    }
}
