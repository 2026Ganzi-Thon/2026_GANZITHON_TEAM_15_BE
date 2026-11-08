package com.ganzithon.teolhanol.domain.care.repository;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareMessageRepository extends JpaRepository<CareMessage, Long> {
}
