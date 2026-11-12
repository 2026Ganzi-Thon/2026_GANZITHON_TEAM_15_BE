package com.ganzithon.teolhanol.domain.worry.controller;

import com.ganzithon.teolhanol.domain.worry.dto.MyWorryListResponse;
import com.ganzithon.teolhanol.domain.worry.service.MyWorryQueryService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WorrySheep", description = "고민 양")
@RestController
@RequestMapping("/worry-sheep")
@RequiredArgsConstructor
public class MyWorryController {

    private final MyWorryQueryService myWorryQueryService;

    @Operation(summary = "내 고민 목록 (FRM-05, FRM-06)",
            description = "status=active(기본, 진행 중) 또는 graduated(보관함). 본인 양만 최신순으로 돌려주며 "
                    + "newWoolCount는 마지막으로 확인한 뒤 새로 받은 조언 수입니다. 진행 중 탭에는 신고로 숨겨진 "
                    + "양이 hidden=true로 함께 나옵니다.")
    @GetMapping("/me")
    public ApiResponse<MyWorryListResponse> mine(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(myWorryQueryService.list(user.id(), status));
    }
}
