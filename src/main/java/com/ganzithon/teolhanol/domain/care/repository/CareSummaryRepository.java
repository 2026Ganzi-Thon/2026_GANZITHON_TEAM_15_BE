package com.ganzithon.teolhanol.domain.care.repository;

import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareSummaryRepository extends JpaRepository<CareSummary, Long> {

    Optional<CareSummary> findByDiaryId(Long diaryId);

    List<CareSummary> findByDiaryIdIn(Collection<Long> diaryIds);

    void deleteByDiaryId(Long diaryId);
}
