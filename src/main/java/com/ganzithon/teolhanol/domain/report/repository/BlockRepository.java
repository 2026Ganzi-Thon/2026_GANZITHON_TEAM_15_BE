package com.ganzithon.teolhanol.domain.report.repository;

import com.ganzithon.teolhanol.domain.report.entity.Block;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Long> {

    /** 두 사용자 사이에 어느 방향으로든 차단이 있는지 */
    @Query("select count(b) > 0 from Block b where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)")
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    /** 내 양(숨김 제외)에 달린 조언의 보낸 사람 id. 내 양에 달린 조언이 아니면 비어 있습니다. */
    @Query("select w.sender.id from Wool w where w.id = :woolId and w.sheep.user.id = :ownerId "
            + "and w.sheep.status <> com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus.HIDDEN")
    Optional<Long> findSenderIdOfReceivedWool(@Param("woolId") Long woolId, @Param("ownerId") Long ownerId);

    @Query("select b from Block b join fetch b.blocked where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    Optional<Block> findByBlockerIdAndBlockedId(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    Optional<Block> findByIdAndBlockerId(Long id, Long blockerId);

    /** 내가 차단한 목동 목록(최신순). 닉네임을 위해 차단 대상을 함께 가져옵니다. */
    @Query("select b from Block b join fetch b.blocked where b.blocker.id = :blockerId order by b.createdAt desc, b.id desc")
    List<Block> findAllByBlockerWithBlocked(@Param("blockerId") Long blockerId);
}
