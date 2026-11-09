package com.ganzithon.teolhanol.domain.care.repository;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareMessageRepository extends JpaRepository<CareMessage, Long> {

    List<CareMessage> findByDiaryIdOrderByIdAsc(Long diaryId);

    Optional<CareMessage> findFirstByDiaryIdOrderByIdDesc(Long diaryId);

    void deleteByDiaryId(Long diaryId);
}
