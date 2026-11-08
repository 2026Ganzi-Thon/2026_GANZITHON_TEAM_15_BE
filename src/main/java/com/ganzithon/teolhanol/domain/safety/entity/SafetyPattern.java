package com.ganzithon.teolhanol.domain.safety.entity;

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

/** 안전 패턴: 위기 키워드, 금칙어, AI 응답 금지 패턴, 개인정보 정규식 (SAF-01, SAF-03~05). */
@Getter
@Entity
@Table(name = "safety_patterns",
        uniqueConstraints = @UniqueConstraint(name = "uk_safety_pattern", columnNames = {"category", "pattern"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatternCategory category;

    public SafetyPattern(String pattern, PatternCategory category) {
        this.pattern = pattern;
        this.category = category;
    }
}
