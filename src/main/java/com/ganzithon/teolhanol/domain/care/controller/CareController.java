package com.ganzithon.teolhanol.domain.care.controller;

import com.ganzithon.teolhanol.domain.care.dto.CareStartRequest;
import com.ganzithon.teolhanol.domain.care.dto.CareStartResponse;
import com.ganzithon.teolhanol.domain.care.service.CareService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Care", description = "AI 케어 대화")
@RestController
@RequestMapping("/diaries/{diaryId}/care")
@RequiredArgsConstructor
public class CareController {

    private final CareService careService;

    @Operation(summary = "케어 방식 고르기 (CARE-01)",
            description = "4가지 방식 중 하나로 케어를 시작하고 AI 첫 메시지를 받습니다. 오늘의 마음 1개당 1회이며, 이미 시작했거나 끝난 기록은 409입니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareStartResponse> start(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable Long diaryId,
                                                @Valid @RequestBody CareStartRequest request) {
        return ApiResponse.ok(careService.start(user.id(), diaryId, request));
    }
}
