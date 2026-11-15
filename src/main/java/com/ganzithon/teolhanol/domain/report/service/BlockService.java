package com.ganzithon.teolhanol.domain.report.service;

import com.ganzithon.teolhanol.domain.report.dto.BlockCreateRequest;
import com.ganzithon.teolhanol.domain.report.dto.BlockResponse;
import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
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
    private final BlockWriter blockWriter;
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

    /**
     * 저장은 {@link BlockWriter}의 새 트랜잭션에서 합니다. 신고처럼 바깥 트랜잭션 안에서 불려도
     * 동시 차단의 UNIQUE 위반이 바깥 트랜잭션을 rollback-only 로 만들지 않습니다.
     */
    private Result blockUserWithResult(Long blockerId, Long targetUserId) {
        if (blockerId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BLOCK_SELF);
        }
        try {
            return blockWriter.insertIfAbsent(blockerId, targetUserId);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 차단이 먼저 저장된 경우: 그 저장 트랜잭션만 롤백됐으니 새 트랜잭션에서 기존 차단을 찾습니다.
            Block winner = blockWriter.find(blockerId, targetUserId).orElseThrow(() -> e);
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
     * 차단 관계는 방향을 나눠 봅니다. 내가 이미 차단한 목동(blocker → owner)은 기존 차단을 돌려줘야 하므로 통과시키고,
     * 나를 차단한 목동(owner → blocker)의 양은 내게 보이지 않아야 하므로 404 입니다.
     */
    private Long ownerOfViewableSheep(Long blockerId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findDetailById(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (sheep.getStatus() != WorrySheepStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Long ownerId = sheep.getUser().getId();
        if (access.isOwner(sheep, blockerId)) {
            return ownerId;
        }
        if (!access.canSeeRisk(sheep, blockerId, access.hasSent(sheepId, blockerId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, ownerId)
                && blockRepository.existsByBlockerIdAndBlockedId(ownerId, blockerId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return ownerId;
    }

    /** 받은 조언만 보이므로, 조언이 달린 양의 주인만 그 조언의 보낸 사람을 차단할 수 있습니다. */
    private Long senderOfMyReceivedWool(Long blockerId, Long woolId) {
        return blockRepository.findSenderIdOfReceivedWool(woolId, blockerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
