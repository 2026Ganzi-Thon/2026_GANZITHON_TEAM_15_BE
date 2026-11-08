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
 * 오늘의 마음. 홈에서 고른 감정 하나와 이어지는 케어 대화 1회에 대응합니다 (DRY-01, CARE-01~03).
 * 사용자가 한 이야기는 care_messages에 암호화해서 저장합니다.
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

    /** 대화 중 사용자 메시지의 위험도 중 가장 높은 등급 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    /** 대화에서 위기 신호가 한 번이라도 나왔는지. true면 마무리해도 털을 쌓지 않습니다 (SAF-02). */
    @Column(name = "crisis_detected", nullable = false)
    private boolean crisisDetected;

    /** 사용자 메시지 수. 3회 이상이어야 털 한 올을 쌓을 수 있습니다 (CARE-02). */
    @Column(name = "user_message_count", nullable = false)
    private int userMessageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_type", length = 20)
    private CareType careType;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_status", nullable = false, length = 20)
    private CareStatus careStatus;

    public Diary(User user, Emotion emotion) {
        this.user = user;
        this.emotion = emotion;
        this.riskLevel = RiskLevel.SAFE;
        this.careStatus = CareStatus.NONE;
    }
}
