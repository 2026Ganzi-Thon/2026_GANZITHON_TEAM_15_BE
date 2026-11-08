package com.ganzithon.teolhanol.domain.safety.entity;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import jakarta.persistence.EntityListeners;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
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

/** 위험도 판정 로그 (SAF-01). 판정 결과와 사유 코드만 남기고 본문은 저장하지 않습니다. */
@Getter
@Entity
@Table(name = "crisis_logs", indexes = @Index(name = "idx_crisis_log_user", columnList = "user_id, created_at"))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrisisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CrisisSource source;

    @Column(name = "keyword_hit", nullable = false)
    private boolean keywordHit;

    /** LLM 판정 결과. LLM 호출이 실패하면 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "llm_level", length = 10)
    private RiskLevel llmLevel;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CrisisLog(User user, CrisisSource source, boolean keywordHit, RiskLevel llmLevel, String reasonCode) {
        this.user = user;
        this.source = source;
        this.keywordHit = keywordHit;
        this.llmLevel = llmLevel;
        this.reasonCode = reasonCode;
    }
}
