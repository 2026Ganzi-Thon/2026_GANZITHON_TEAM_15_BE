package com.ganzithon.teolhanol.domain.worry.controller;

import com.ganzithon.teolhanol.domain.worry.dto.WorrySheepDetailResponse;
import com.ganzithon.teolhanol.domain.worry.service.WorrySheepDetailService;
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

@Tag(name = "WorrySheep", description = "고민 양")
@RestController
@RequestMapping("/worry-sheep")
@RequiredArgsConstructor
public class WorrySheepController {

    private final WorrySheepDetailService detailService;

    @Operation(summary = "고민 양 상세 (FRM-03, FRM-05)",
            description = "고민과 양의 단계, 작성자 닉네임을 돌려줍니다. 받은 조언 목록(wools)은 고민 주인에게만 담기며 "
                    + "털 개수 숫자는 없습니다. 숨김·졸업(타인)·차단·보호 양 접근은 모두 404입니다.")
    @GetMapping("/{id}")
    public ApiResponse<WorrySheepDetailResponse> detail(@AuthenticationPrincipal AuthUser user,
                                                        @PathVariable Long id) {
        return ApiResponse.ok(detailService.get(user.id(), id));
    }
}
