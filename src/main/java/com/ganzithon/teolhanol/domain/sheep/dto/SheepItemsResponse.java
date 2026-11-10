package com.ganzithon.teolhanol.domain.sheep.dto;

import java.util.List;

/**
 * 꾸미기 화면(ME-02) 데이터.
 *
 * @param yarnCount 지금 가진 털실
 * @param spentYarn 이번 요청으로 쓴 털실 (조회에서는 0)
 * @param items     전체 아이템 목록(고정 순서)과 가진 여부·착용 여부
 */
public record SheepItemsResponse(int yarnCount, int spentYarn, List<Item> items) {

    public record Item(String code, String name, int yarnCost, boolean owned, boolean equipped) {
    }
}
