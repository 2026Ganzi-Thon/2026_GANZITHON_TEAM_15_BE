package com.ganzithon.teolhanol.domain.safety.repository;

import com.ganzithon.teolhanol.domain.safety.entity.CrisisLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrisisLogRepository extends JpaRepository<CrisisLog, Long> {
}
