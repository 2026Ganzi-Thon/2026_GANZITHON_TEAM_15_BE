package com.ganzithon.teolhanol.domain.diary.repository;

import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
}
