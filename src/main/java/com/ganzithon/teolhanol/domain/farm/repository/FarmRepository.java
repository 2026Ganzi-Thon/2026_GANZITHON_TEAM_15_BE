package com.ganzithon.teolhanol.domain.farm.repository;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmRepository extends JpaRepository<Farm, Long> {
}
