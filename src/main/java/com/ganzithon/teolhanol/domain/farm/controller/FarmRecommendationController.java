package com.ganzithon.teolhanol.domain.farm.controller;

import com.ganzithon.teolhanol.domain.farm.dto.FarmRecommendationResponse;
import com.ganzithon.teolhanol.domain.farm.service.FarmRecommendationService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Farm", description = "고민 목장")
@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class FarmRecommendationController {

    private final FarmRecommendationService farmRecommendationService;

    @Operation(summary = "케어 마무리에서 목장 추천 (FRM-07)",
            description = "기록의 감정과 케어 방식으로 목장 1곳을 추천합니다. 대화 원문은 읽거나 전달하지 않으며, "
                    + "고민 올리기 화면은 비어 있는 채로 목장만 미리 골라집니다. 본인 기록만 조회할 수 있습니다.")
    @GetMapping("/{id}/farm-recommendation")
    public ApiResponse<FarmRecommendationResponse> recommend(
            @AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(farmRecommendationService.recommend(user.id(), id));
    }
}
