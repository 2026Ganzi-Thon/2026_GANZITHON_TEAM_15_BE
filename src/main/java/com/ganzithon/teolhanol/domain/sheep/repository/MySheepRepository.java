package com.ganzithon.teolhanol.domain.sheep.repository;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MySheepRepository extends JpaRepository<MySheep, Long> {

    Optional<MySheep> findByUserId(Long userId);

    /** 털을 쌓을 때 쓰는 잠금 조회. 서로 다른 마음을 동시에 마무리해도 털이 덮어써지지 않게 합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MySheep s where s.user.id = :userId")
    Optional<MySheep> findByUserIdForUpdate(@Param("userId") Long userId);
}
