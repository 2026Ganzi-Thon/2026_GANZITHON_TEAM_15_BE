package com.ganzithon.teolhanol.domain.diary.repository;

import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    /** 기준 시각 이후에 시작한 대화 수 (하루 시작 한도 검사용) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
