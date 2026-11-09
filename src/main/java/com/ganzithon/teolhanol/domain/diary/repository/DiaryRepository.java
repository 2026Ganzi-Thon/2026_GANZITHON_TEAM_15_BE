package com.ganzithon.teolhanol.domain.diary.repository;

import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByIdAndUserId(Long id, Long userId);

    /** 본인 기록만 잠가서 가져옵니다. 동시에 케어를 두 번 시작하는 것을 막습니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Diary d where d.id = :id and d.user.id = :userId")
    Optional<Diary> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** 기준 시각 이후에 시작한 대화 수 (하루 시작 한도 검사용) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
