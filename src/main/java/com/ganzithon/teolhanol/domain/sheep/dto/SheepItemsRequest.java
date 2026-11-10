package com.ganzithon.teolhanol.domain.sheep.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 나의 양 꾸미기 저장 (SHP-03). 둘 다 비워 보내면 아무것도 바꾸지 않고 현재 상태를 돌려줍니다.
 *
 * @param craft    지금 털실로 만들 아이템 코드. 이미 가진 아이템은 털실을 쓰지 않고 건너뜁니다
 * @param equipped 저장 후 착용할 아이템 코드 전체. 없는 코드는 해제됩니다. null이면 착용 상태를 바꾸지 않습니다
 */
public record SheepItemsRequest(
        @Schema(example = "[\"SCARF\", \"HAT\"]") List<String> craft,
        @Schema(example = "[\"SCARF\"]") List<String> equipped
) {
}
