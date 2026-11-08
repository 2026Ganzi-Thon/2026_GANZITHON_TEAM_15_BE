package com.ganzithon.teolhanol.domain.worry.entity;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
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
 * 고민 양. 털 없는 양으로 태어나 다른 사용자의 조언을 받으며 풍성해집니다 (FRM-04).
 * risk_level이 CAUTION이면 '보호 양'으로, 조언 이력이 충분한 사용자에게만 보입니다.
 * (farm_id, status, wool_count, created_at) 인덱스로 '털 적은 양 먼저' 조회를 지원합니다 (FRM-02).
 */
@Getter
@Entity
@Table(name = "worry_sheep", indexes = {
        @Index(name = "idx_worry_farm_status_wool_created", columnList = "farm_id, status, wool_count, created_at"),
        @Index(name = "idx_worry_user_created", columnList = "user_id, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorrySheep extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Column(nullable = false, length = 200)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Column(name = "wool_count", nullable = false)
    private int woolCount;

    @Column(nullable = false)
    private int stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorrySheepStatus status;

    @Column(name = "graduated_at")
    private LocalDateTime graduatedAt;

    public WorrySheep(User user, Farm farm, String content, RiskLevel riskLevel) {
        this.user = user;
        this.farm = farm;
        this.content = content;
        this.riskLevel = riskLevel;
        this.woolCount = 0;
        this.stage = 1;
        this.status = WorrySheepStatus.ACTIVE;
    }
}
