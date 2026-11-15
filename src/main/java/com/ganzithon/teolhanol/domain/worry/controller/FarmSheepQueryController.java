package com.ganzithon.teolhanol.domain.worry.controller;

import com.ganzithon.teolhanol.domain.worry.dto.FarmSheepPageResponse;
import com.ganzithon.teolhanol.domain.worry.service.FarmSheepQueryService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Farm", description = "고민 목장")
@RestController
@RequestMapping("/farms")
@RequiredArgsConstructor
public class FarmSheepQueryController {

    private final FarmSheepQueryService farmSheepQueryService;

    @Operation(summary = "풀밭의 고민 양 목록 (FRM-01 FARM-03)",
            description = "주제 탭의 고민 양을 털 적은 순 → 오래된 순으로 한 페이지(20마리)씩 돌려줍니다. "
                    + "내가 이미 조언을 보낸 양은 뒤로 갑니다. farmCode는 CAREER·RELATIONSHIP·BURNOUT·SELF 또는 all(전체 탭)이며 "
                    + "대소문자를 가리지 않습니다. 졸업·숨김 양, 내 양, 차단 관계 사용자의 양은 빠지고, "
                    + "'주의'(보호) 양은 조언을 5회 이상 보낸 사용자에게만 보입니다. 작성자는 닉네임만 내려주며, "
                    + "상대 시간 문구는 createdAt으로 프론트에서 만듭니다. 없는 목장이면 404입니다.")
    @GetMapping("/{farmCode}/sheep")
    public ApiResponse<FarmSheepPageResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "목장 코드 또는 all", example = "all") @PathVariable String farmCode,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(farmSheepQueryService.list(user.id(), farmCode, page));
    }
}
