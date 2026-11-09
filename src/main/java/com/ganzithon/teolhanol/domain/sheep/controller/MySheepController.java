package com.ganzithon.teolhanol.domain.sheep.controller;

import com.ganzithon.teolhanol.domain.sheep.dto.MySheepResponse;
import com.ganzithon.teolhanol.domain.sheep.service.MySheepService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MySheep", description = "나의 양")
@RestController
@RequestMapping("/my-sheep")
@RequiredArgsConstructor
public class MySheepController {

    private final MySheepService mySheepService;

    @Operation(summary = "나의 양 조회 (SHP-01)",
            description = "단계·누적 털·다음 단계까지 남은 털(진행 바)·털실·착용 아이템. 털 3올마다 한 단계, 5단계까지입니다.")
    @GetMapping
    public ApiResponse<MySheepResponse> get(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(mySheepService.get(user.id()));
    }
}
