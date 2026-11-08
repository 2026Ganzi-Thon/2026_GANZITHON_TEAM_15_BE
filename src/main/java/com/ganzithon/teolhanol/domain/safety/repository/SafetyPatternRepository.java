package com.ganzithon.teolhanol.domain.safety.repository;

import com.ganzithon.teolhanol.domain.safety.entity.PatternCategory;
import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyPatternRepository extends JpaRepository<SafetyPattern, Long> {

    boolean existsByCategoryAndPattern(PatternCategory category, String pattern);

    List<SafetyPattern> findAllByCategory(PatternCategory category);
}
