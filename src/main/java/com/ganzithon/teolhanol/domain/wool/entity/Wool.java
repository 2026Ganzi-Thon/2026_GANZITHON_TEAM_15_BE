package com.ganzithon.teolhanol.domain.wool.entity;

import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
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

/** 고민 양에 보낸 조언(털 한 올). (sheep_id, sender_id) 유니크로 1인 1조언을 보장합니다 (WOL-01). */
@Getter
@Entity
@Table(name = "wools",
        uniqueConstraints = @UniqueConstraint(name = "uk_wool_sheep_sender", columnNames = {"sheep_id", "sender_id"}),
        indexes = @Index(name = "idx_wool_sender", columnList = "sender_id"))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheep_id", nullable = false)
    private WorrySheep sheep;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 100)
    private String content;

    /** 어조 다시 확인(SAF-04)에서 바꿀 문장을 제안받고도 그대로 보낸 조언인지 */
    @Column(name = "sent_as_is", nullable = false)
    private boolean sentAsIs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Wool(WorrySheep sheep, User sender, String content, boolean sentAsIs) {
        this.sheep = sheep;
        this.sender = sender;
        this.content = content;
        this.sentAsIs = sentAsIs;
    }
}
