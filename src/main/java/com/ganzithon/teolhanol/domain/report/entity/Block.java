package com.ganzithon.teolhanol.domain.report.entity;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.global.entity.BaseTimeEntity;
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

/** 사용자 차단 (RPT-02). 서로의 고민 양이 목장에서 보이지 않습니다. */
@Getter
@Entity
@Table(name = "blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_block_pair", columnNames = {"blocker_id", "blocked_id"}),
        indexes = @Index(name = "idx_block_blocked", columnList = "blocked_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    public Block(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }
}
