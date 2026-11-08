package com.ganzithon.teolhanol.domain.farm.entity;

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

/** 고민 주제별 목장. 고정 4곳을 시드 데이터로 넣습니다 (FRM-01). */
@Getter
@Entity
@Table(name = "farms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 30)
    private String name;

    /** 목장 화면 주제 탭에 보이는 짧은 이름 (예: 진로) */
    @Column(name = "tab_name", nullable = false, length = 10)
    private String tabName;

    @Column(nullable = false, length = 100)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Farm(String code, String name, String tabName, String description, int sortOrder) {
        this.code = code;
        this.name = name;
        this.tabName = tabName;
        this.description = description;
        this.sortOrder = sortOrder;
    }
}
