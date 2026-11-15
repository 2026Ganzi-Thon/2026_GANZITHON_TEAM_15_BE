package com.ganzithon.teolhanol.domain.wool.repository;

import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WoolRepository extends JpaRepository<Wool, Long> {

    boolean existsBySheepIdAndSenderId(Long sheepId, Long senderId);

    /** 고민 주인에게 보여줄 받은 조언. 보낸 사람 닉네임까지 한 번에 가져옵니다(최신순). */
    @Query("select w from Wool w join fetch w.sender where w.sheep.id = :sheepId order by w.createdAt desc, w.id desc")
    List<Wool> findAllWithSenderBySheepId(@Param("sheepId") Long sheepId);
}
