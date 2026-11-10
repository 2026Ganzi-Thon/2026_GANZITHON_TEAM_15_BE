package com.ganzithon.teolhanol.domain.sheep.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 나의 양 꾸미기 아이템 고정 데이터 (SHP-03). application.yml의 app.sheep-items.items에서 코드·이름·필요 털실을 바꿉니다.
 * 목록 순서가 화면에 보이는 순서입니다.
 */
@Validated
@ConfigurationProperties(prefix = "app.sheep-items")
public record SheepItemProperties(@Valid List<Item> items) {

    public SheepItemProperties {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(@NotBlank String code, @NotBlank String name, @PositiveOrZero int yarnCost) {
    }
}
