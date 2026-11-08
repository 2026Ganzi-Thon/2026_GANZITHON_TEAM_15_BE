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
 * 회원. 이메일·전화번호·실명은 수집하지 않습니다 (ACC-01).
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

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    /** 털실: 조언을 보내면 +1, 나의 양 꾸미기에 사용 */
    @Column(name = "yarn_count", nullable = false)
    private int yarnCount;

    /** 반짝이는 털실: 고마워요 답례를 받으면 +1 */
    @Column(name = "shiny_yarn_count", nullable = false)
    private int shinyYarnCount;

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

    public User(String loginId, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }
}
