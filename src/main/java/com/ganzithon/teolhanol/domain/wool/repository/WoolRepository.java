package com.ganzithon.teolhanol.domain.wool.repository;

import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WoolRepository extends JpaRepository<Wool, Long> {

    boolean existsBySheepIdAndSenderId(Long sheepId, Long senderId);

    /** 고민 주인에게 보여줄 받은 조언. 보낸 사람 닉네임까지 한 번에 가져옵니다(최신순). */
    @Query("select w from Wool w join fetch w.sender where w.sheep.id = :sheepId order by w.createdAt desc, w.id desc")
    List<Wool> findAllWithSenderBySheepId(@Param("sheepId") Long sheepId);

    /** 위와 같되 신고 누적으로 숨겨진 조언(RPT-01)은 뺍니다. */
    @Query("select w from Wool w join fetch w.sender where w.sheep.id = :sheepId and w.hidden = false "
            + "order by w.createdAt desc, w.id desc")
    List<Wool> findAllVisibleWithSenderBySheepId(@Param("sheepId") Long sheepId);

    /** 신고 누적 숨김(RPT-01)을 위한 잠금 조회. 조언 행만 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wool w where w.id = :id")
    Optional<Wool> findByIdForUpdate(@Param("id") Long id);
}
