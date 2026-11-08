package com.ganzithon.teolhanol.domain.sheep.entity;

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

/** 털실로 만든 나의 양 아이템 (SHP-03). item_code는 고정 데이터의 아이템 코드입니다. */
@Getter
@Entity
@Table(name = "my_sheep_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_my_sheep_item", columnNames = {"my_sheep_id", "item_code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MySheepItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "my_sheep_id", nullable = false)
    private MySheep mySheep;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(nullable = false)
    private boolean equipped;

    public MySheepItem(MySheep mySheep, String itemCode) {
        this.mySheep = mySheep;
        this.itemCode = itemCode;
    }
}
