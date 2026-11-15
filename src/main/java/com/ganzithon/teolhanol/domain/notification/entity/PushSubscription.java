package com.ganzithon.teolhanol.domain.notification.entity;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저 웹 푸시 구독 (WOL-02). 브라우저(서비스워커)마다 endpoint가 하나이며, 같은 브라우저에서 다른 계정으로 로그인하면
 * 소유자만 바뀝니다. 탈퇴하면 함께 지웁니다({@code PushSubscriptionEraser}).
 */
@Getter
@Entity
@Table(name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_endpoint", columnNames = "endpoint"),
        indexes = @Index(name = "idx_push_subscription_user", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

    public static final int ENDPOINT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = ENDPOINT_MAX_LENGTH)
    private String endpoint;

    /** 브라우저 공개키 (P-256, base64url) */
    @Column(nullable = false, length = 200)
    private String p256dh;

    /** 인증 비밀값 (16바이트, base64url) */
    @Column(nullable = false, length = 100)
    private String auth;

    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    /** 같은 브라우저가 다시 구독했습니다. 지금 로그인한 사용자로 소유자를 바꾸고 키를 새로 받습니다. */
    public void reassign(User user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
