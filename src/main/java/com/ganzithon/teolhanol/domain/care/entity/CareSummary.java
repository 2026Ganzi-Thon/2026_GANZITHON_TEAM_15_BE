package com.ganzithon.teolhanol.domain.care.entity;

import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.global.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** 케어 마무리 카드 '오늘 받은 말' (CARE-03). 오늘의 마음 1개당 1개. summary는 DB에 암호화해서 저장합니다. */
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

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    public CareSummary(Diary diary, String summary, LocalDateTime completedAt) {
        this.diary = diary;
        this.summary = summary;
        this.completedAt = completedAt;
    }
}
