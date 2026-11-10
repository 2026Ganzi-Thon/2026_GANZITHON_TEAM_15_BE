package com.ganzithon.teolhanol.domain.user.entity;

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

/**
 * 회원. 이메일은 로그인에만 쓰고, 전화번호·실명은 수집하지 않습니다 (ACC-01).
 * 로그인 잠금(ACC-02)과 위기 배너(SAF-02)를 위한 컬럼을 명세서 테이블에 추가했습니다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인용 이메일. 다른 사용자에게는 노출하지 않습니다. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    /** 털실: 조언을 보내면 +1, 나의 양 꾸미기에 사용 */
    @Column(name = "yarn_count", nullable = false)
    private int yarnCount;

    /** 보낸 조언 수: '주의' 고민 양 노출 기준(5회 이상)에 사용 */
    @Column(name = "sent_wool_count", nullable = false)
    private int sentWoolCount;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** 이 시각까지 상단에 상담 안내 배너를 보여줍니다 (SAF-02, RPT-01) */
    @Column(name = "crisis_banner_until")
    private LocalDateTime crisisBannerUntil;

    public User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    /** 털실을 씁니다 (나의 양 꾸미기). 모자라면 false이고 아무것도 바꾸지 않습니다. */
    public boolean spendYarn(int amount) {
        if (amount < 0 || yarnCount < amount) {
            return false;
        }
        yarnCount -= amount;
        return true;
    }

    /** 위기 판정 후 상담 안내 배너를 켭니다 (SAF-02). 이미 더 늦게 끝나는 배너가 있으면 유지합니다. */
    public void activateCrisisBanner(LocalDateTime until) {
        if (crisisBannerUntil == null || crisisBannerUntil.isBefore(until)) {
            crisisBannerUntil = until;
        }
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 로그인 실패를 기록합니다. 이전 잠금이 풀렸다면 처음부터 다시 세고,
     * 최대 횟수에 닿으면 잠그면서 횟수를 0으로 되돌립니다(잠금이 풀리면 다시 5번 시도 가능).
     *
     * @return 이번 실패로 잠겼다면 해제 시각, 아니면 null
     */
    public LocalDateTime recordLoginFailure(LocalDateTime now, int maxFailures, int lockMinutes) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            lockedUntil = null;
            loginFailCount = 0;
        }
        loginFailCount++;
        if (loginFailCount >= maxFailures) {
            lockedUntil = now.plusMinutes(lockMinutes);
            loginFailCount = 0;
            return lockedUntil;
        }
        return null;
    }

    public void clearLoginFailures() {
        loginFailCount = 0;
        lockedUntil = null;
    }
}
