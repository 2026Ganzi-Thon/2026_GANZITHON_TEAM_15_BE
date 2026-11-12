package com.ganzithon.teolhanol.domain.worry.controller;

import com.ganzithon.teolhanol.domain.worry.dto.WorryGraduateResponse;
import com.ganzithon.teolhanol.domain.worry.service.WorryGraduationService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WorrySheep", description = "고민 양")
@RestController
@RequestMapping("/worry-sheep")
@RequiredArgsConstructor
public class WorryGraduationController {

    private final WorryGraduationService graduationService;

    @Operation(summary = "고민 양 직접 졸업 (FRM-06)",
            description = "고민 주인이 양을 보관함으로 보냅니다. 주인이 아니거나 없는·숨김 양은 404이고, "
                    + "이미 졸업한 양은 오류 없이 현재 상태를 그대로 돌려줍니다.")
    @PostMapping("/{id}/graduate")
    public ApiResponse<WorryGraduateResponse> graduate(@AuthenticationPrincipal AuthUser user,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(graduationService.graduate(user.id(), id));
    }
}
