package com.ganzithon.teolhanol.domain.safety.controller;

import com.ganzithon.teolhanol.domain.safety.dto.CrisisBannerResponse;
import com.ganzithon.teolhanol.domain.safety.dto.CrisisHelpResponse;
import com.ganzithon.teolhanol.domain.safety.service.CrisisBannerService;
import com.ganzithon.teolhanol.domain.safety.service.CrisisHelpInfo;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Safety", description = "안전·상담 연결")
@RestController
@RequestMapping("/help/crisis")
@RequiredArgsConstructor
public class CrisisHelpController {

    private final CrisisBannerService bannerService;

    @Operation(summary = "상담 연결 정보 (SAF-02)",
            description = "비회원도 볼 수 있습니다. 자살예방 상담전화 109, 정신건강 위기상담 1577-0199(두 곳 모두 24시간), 위험할 때 119.")
    @GetMapping
    public ApiResponse<CrisisHelpResponse> info() {
        return ApiResponse.ok(CrisisHelpInfo.INFO);
    }

    @Operation(summary = "24시간 상담 배너 상태 (SAF-02)",
            description = "위기 판정 뒤 정해진 시간 동안 active=true 이고 만료 시각을 내려줍니다. 로그인이 필요합니다.")
    @GetMapping("/banner")
    public ApiResponse<CrisisBannerResponse> banner(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(bannerService.get(user.id()));
    }
}
