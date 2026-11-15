package com.ganzithon.teolhanol.domain.worry.repository;

import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorrySheepRepository extends JpaRepository<WorrySheep, Long> {

    /** 기준 시각 이후 이 사용자가 만든 고민 양 수. 숨김·졸업 포함 (하루 한도 검사용, FRM-04) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
