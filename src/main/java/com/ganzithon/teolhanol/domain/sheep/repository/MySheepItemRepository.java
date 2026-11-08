package com.ganzithon.teolhanol.domain.sheep.repository;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheepItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MySheepItemRepository extends JpaRepository<MySheepItem, Long> {
}
