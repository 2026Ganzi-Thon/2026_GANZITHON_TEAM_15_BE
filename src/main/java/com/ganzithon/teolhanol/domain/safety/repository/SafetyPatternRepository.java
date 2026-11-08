package com.ganzithon.teolhanol.domain.safety.repository;

import com.ganzithon.teolhanol.domain.safety.entity.SafetyPattern;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyPatternRepository extends JpaRepository<SafetyPattern, Long> {
}
