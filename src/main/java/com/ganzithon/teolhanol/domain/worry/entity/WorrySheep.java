package com.ganzithon.teolhanol.domain.worry.entity;

import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.global.entity.BaseTimeEntity;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.policy.SheepStage;
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

    /** 주인이 상세를 마지막으로 확인한 시각. 이 뒤에 도착한 털이 '새 털'입니다 (FRM-05). null이면 아직 확인 전. */
    @Column(name = "owner_checked_at")
    private LocalDateTime ownerCheckedAt;

    public WorrySheep(User user, Farm farm, String content, RiskLevel riskLevel) {
        this.user = user;
        this.farm = farm;
        this.content = content;
        this.riskLevel = riskLevel;
        this.woolCount = 0;
        this.stage = 1;
        this.status = WorrySheepStatus.ACTIVE;
    }

    /** 졸업시킵니다 (FRM-06). */
    public void graduate() {
        this.status = WorrySheepStatus.GRADUATED;
        this.graduatedAt = LocalDateTime.now();
    }

    /** 주인이 상세를 확인했음을 기록합니다 (FRM-05). */
    public void markOwnerChecked(LocalDateTime now) {
        this.ownerCheckedAt = now;
    }

    /** 운영 정책·신고로 목장에서 숨깁니다. */
    public void hide() {
        this.status = WorrySheepStatus.HIDDEN;
    }

    /**
     * 조언(털) 한 올을 받습니다 (WOL-01). 털 상한(cap)에 닿았으면 더 늘리지 않고, 단계는 받은 털 수로 다시 정합니다.
     * 상한 도달 시 졸업은 조언 보내기(WoolSendWriter)가 같은 트랜잭션에서 {@link #graduate()}로 처리합니다.
     */
    public void addWool(int cap) {
        if (woolCount >= cap) {
            return;
        }
        woolCount++;
        stage = SheepStage.ofWorrySheep(woolCount).getLevel();
    }
}
