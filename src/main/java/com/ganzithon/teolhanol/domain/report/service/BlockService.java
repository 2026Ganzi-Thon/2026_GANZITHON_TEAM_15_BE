package com.ganzithon.teolhanol.domain.report.service;

import com.ganzithon.teolhanol.domain.report.dto.BlockCreateRequest;
import com.ganzithon.teolhanol.domain.report.dto.BlockResponse;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.domain.worry.service.WorrySheepAccess;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 차단과 차단 관리 (RPT-02). 대상 사용자는 양·조언 id로 서버가 찾습니다. */
@Service
@RequiredArgsConstructor
public class BlockService {

    /** 차단 결과. created=false 면 이미 차단돼 있던 기존 차단입니다. */
    public record Result(Block block, boolean created) {
    }

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final WorrySheepRepository worrySheepRepository;
    private final WorrySheepAccess access;

    /** API 용: 양·조언 id로 대상을 찾아 차단합니다. */
    public Result block(Long blockerId, BlockCreateRequest request) {
        if (request == null || request.targetType() == null || request.targetId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Long targetUserId = switch (request.targetType()) {
            case WORRY_SHEEP -> ownerOfViewableSheep(blockerId, request.targetId());
            case WOOL -> senderOfMyReceivedWool(blockerId, request.targetId());
        };
        return blockUserWithResult(blockerId, targetUserId);
    }

    /** 신고(#35) 등 다른 기능이 재사용합니다. 이미 차단했다면 기존 차단을 돌려줍니다(멱등). */
    public Block blockUser(Long blockerId, Long targetUserId) {
        return blockUserWithResult(blockerId, targetUserId).block();
    }

    private Result blockUserWithResult(Long blockerId, Long targetUserId) {
        if (blockerId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BLOCK_SELF);
        }
        var existing = blockRepository.findByBlockerIdAndBlockedId(blockerId, targetUserId);
        if (existing.isPresent()) {
            return new Result(existing.get(), false);
        }
        try {
            blockRepository.saveAndFlush(new Block(
                    userRepository.getReferenceById(blockerId), userRepository.getReferenceById(targetUserId)));
            // 닉네임(지연 로딩)까지 읽을 수 있게 다시 조회합니다.
            return new Result(blockRepository.findByBlockerIdAndBlockedId(blockerId, targetUserId).orElseThrow(), true);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 차단이 들어온 경우: 기존 차단을 돌려줍니다.
            Block winner = blockRepository.findByBlockerIdAndBlockedId(blockerId, targetUserId).orElseThrow(() -> e);
            return new Result(winner, false);
        }
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> list(Long blockerId) {
        return blockRepository.findAllByBlockerWithBlocked(blockerId).stream().map(BlockResponse::from).toList();
    }

    @Transactional
    public void unblock(Long blockerId, Long blockId) {
        Block block = blockRepository.findByIdAndBlockerId(blockId, blockerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        blockRepository.delete(block);
    }

    /**
     * 숨김·졸업 양과 볼 수 없는 보호 양은 없는 양처럼 404 입니다(닉네임을 흘리지 않도록, {@link WorrySheepAccess} 기준).
     * 이미 차단한 목동을 다시 차단하면 기존 차단을 돌려줘야 하므로 차단 관계는 여기서 보지 않습니다.
     */
    private Long ownerOfViewableSheep(Long blockerId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findDetailById(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (sheep.getStatus() != WorrySheepStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!access.isOwner(sheep, blockerId)
                && !access.canSeeRisk(sheep, blockerId, access.hasSent(sheepId, blockerId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return sheep.getUser().getId();
    }

    /** 받은 조언만 보이므로, 조언이 달린 양의 주인만 그 조언의 보낸 사람을 차단할 수 있습니다. */
    private Long senderOfMyReceivedWool(Long blockerId, Long woolId) {
        return blockRepository.findSenderIdOfReceivedWool(woolId, blockerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
