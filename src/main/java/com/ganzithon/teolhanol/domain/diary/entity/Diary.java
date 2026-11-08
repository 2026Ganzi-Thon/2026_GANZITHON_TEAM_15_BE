package com.ganzithon.teolhanol.domain.diary.entity;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.global.entity.BaseTimeEntity;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
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

/**
 * 감정 일기. 일기 하나가 케어 1회와 기록 양 1마리에 대응합니다 (DRY-01, SHP-02).
 * content는 #3에서 암호화 저장으로 바뀝니다.
 */
@Getter
@Entity
@Table(name = "diaries", indexes = {
        @Index(name = "idx_diary_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_diary_user_care", columnList = "user_id, care_type, care_status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Emotion emotion;

    /** 감정 강도 1~5 */
    @Column(nullable = false)
    private int intensity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_type", length = 20)
    private CareType careType;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_status", nullable = false, length = 20)
    private CareStatus careStatus;

    public Diary(User user, Emotion emotion, int intensity, String content, RiskLevel riskLevel) {
        this.user = user;
        this.emotion = emotion;
        this.intensity = intensity;
        this.content = content;
        this.riskLevel = riskLevel;
        this.careStatus = riskLevel.isCrisis() ? CareStatus.STOPPED : CareStatus.NONE;
    }
}
