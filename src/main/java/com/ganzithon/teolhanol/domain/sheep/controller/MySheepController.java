package com.ganzithon.teolhanol.domain.sheep.controller;

import com.ganzithon.teolhanol.domain.sheep.dto.MySheepResponse;
import com.ganzithon.teolhanol.domain.sheep.dto.SheepItemsRequest;
import com.ganzithon.teolhanol.domain.sheep.dto.SheepItemsResponse;
import com.ganzithon.teolhanol.domain.sheep.service.MySheepService;
import com.ganzithon.teolhanol.domain.sheep.service.SheepItemService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MySheep", description = "나의 양")
@RestController
@RequestMapping("/my-sheep")
@RequiredArgsConstructor
public class MySheepController {

    private final MySheepService mySheepService;
    private final SheepItemService sheepItemService;

    @Operation(summary = "나의 양 조회 (SHP-01)",
            description = "단계·누적 털·다음 단계까지 남은 털(진행 바)·털실·착용 아이템. 털 3올마다 한 단계, 5단계까지입니다.")
    @GetMapping
    public ApiResponse<MySheepResponse> get(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(mySheepService.get(user.id()));
    }

    @Operation(summary = "꾸미기 화면 데이터 (SHP-03 ME-02)",
            description = "가진 털실, 전체 아이템(목도리·모자·방울·리본)과 필요 털실, 가진 여부, 착용 여부.")
    @GetMapping("/items")
    public ApiResponse<SheepItemsResponse> items(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(sheepItemService.get(user.id()));
    }

    @Operation(summary = "아이템 만들기·착용 저장 (SHP-03)",
            description = "craft의 아이템을 털실로 만들고(이미 가진 건 건너뜀) equipped에 있는 아이템만 착용합니다. "
                    + "털실이 모자라면 409이며 아무것도 바뀌지 않습니다. 꾸미기는 털 단계에 영향이 없습니다.")
    @PutMapping("/items")
    public ApiResponse<SheepItemsResponse> saveItems(@AuthenticationPrincipal AuthUser user,
                                                     @RequestBody SheepItemsRequest request) {
        return ApiResponse.ok(sheepItemService.save(user.id(), request));
    }
}
