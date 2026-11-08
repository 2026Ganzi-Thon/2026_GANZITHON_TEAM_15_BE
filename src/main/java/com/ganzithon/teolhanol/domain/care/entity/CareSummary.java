package com.ganzithon.teolhanol.domain.care.entity;

import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 케어 마무리 요약 카드 (CARE-03). 일기 1개당 1개. summary는 #3에서 암호화 저장으로 바뀝니다. */
@Getter
@Entity
@Table(name = "care_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_id", nullable = false, unique = true)
    private Diary diary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CareRating rating;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    public CareSummary(Diary diary, String summary, CareRating rating, LocalDateTime completedAt) {
        this.diary = diary;
        this.summary = summary;
        this.rating = rating;
        this.completedAt = completedAt;
    }
}
