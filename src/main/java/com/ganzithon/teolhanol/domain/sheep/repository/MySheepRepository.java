package com.ganzithon.teolhanol.domain.sheep.repository;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MySheepRepository extends JpaRepository<MySheep, Long> {
}
