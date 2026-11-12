package com.ganzithon.teolhanol.domain.report.service;

import com.ganzithon.teolhanol.domain.report.entity.Block;
import com.ganzithon.teolhanol.domain.report.repository.BlockRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단 행 저장 (RPT-02). 호출한 쪽(예: 신고)의 트랜잭션과 떨어진 새 트랜잭션에서 저장합니다.
 * 같은 차단이 동시에 들어와 UNIQUE(blocker_id, blocked_id) 위반이 나도 이 트랜잭션만 롤백되므로,
 * 바깥 트랜잭션이 rollback-only 가 되지 않습니다. 위반 뒤 다시 조회도 새 트랜잭션에서 해야 먼저 저장된 행이 보입니다
 * (MySQL REPEATABLE READ 에서는 바깥 트랜잭션의 스냅샷으로는 보이지 않을 수 있습니다).
 */
@Component
@RequiredArgsConstructor
public class BlockWriter {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    /**
     * 차단이 있으면 그대로, 없으면 새로 만들어 돌려줍니다. 돌려준 차단은 트랜잭션 밖에서도 대상 닉네임을 읽을 수 있습니다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 동시에 같은 차단이 먼저 저장된 경우(이 트랜잭션은 롤백)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BlockService.Result insertIfAbsent(Long blockerId, Long blockedId) {
        Optional<Block> existing = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId);
        if (existing.isPresent()) {
            return new BlockService.Result(existing.get(), false);
        }
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Block saved = blockRepository.saveAndFlush(new Block(userRepository.getReferenceById(blockerId), blocked));
        return new BlockService.Result(saved, true);
    }

    /** 새 트랜잭션(새 스냅샷)에서 차단을 찾습니다. 대상 사용자를 함께 가져옵니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Block> find(Long blockerId, Long blockedId) {
        return blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}
