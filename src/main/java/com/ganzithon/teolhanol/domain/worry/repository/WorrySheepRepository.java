package com.ganzithon.teolhanol.domain.worry.repository;

import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorrySheepRepository extends JpaRepository<WorrySheep, Long> {

    /** 상세 화면용: 작성자와 목장을 함께 가져옵니다. */
    @Query("select s from WorrySheep s join fetch s.user join fetch s.farm where s.id = :id")
    Optional<WorrySheep> findDetailById(@Param("id") Long id);
}
