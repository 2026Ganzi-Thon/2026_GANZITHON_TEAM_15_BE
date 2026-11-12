package com.ganzithon.teolhanol.domain.wool.repository;

import com.ganzithon.teolhanol.domain.wool.entity.Thanks;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThanksRepository extends JpaRepository<Thanks, Long> {

    /** 이 고민 양의 조언 중 고마워요를 받은 조언 id들 */
    @Query("select t.wool.id from Thanks t where t.wool.sheep.id = :sheepId")
    List<Long> findThankedWoolIdsBySheepId(@Param("sheepId") Long sheepId);
}
