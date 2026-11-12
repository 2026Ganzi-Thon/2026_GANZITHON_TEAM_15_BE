package com.ganzithon.teolhanol.domain.report.repository;

import com.ganzithon.teolhanol.domain.report.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Long> {

    /** 두 사용자 사이에 어느 방향으로든 차단이 있는지 */
    @Query("select count(b) > 0 from Block b where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)")
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);
}
