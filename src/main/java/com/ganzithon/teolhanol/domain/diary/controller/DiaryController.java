package com.ganzithon.teolhanol.domain.diary.controller;

import com.ganzithon.teolhanol.domain.diary.dto.DiaryCreateRequest;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryCreateResponse;
import com.ganzithon.teolhanol.domain.diary.service.DiaryService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Diary", description = "오늘의 마음·기록")
@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "오늘 마음 고르기 (DRY-01)",
            description = "감정 8종 중 하나로 오늘의 마음 기록을 만들고 AI 첫 마디를 돌려줍니다. 하루 3회까지이며 넘으면 429입니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiaryCreateResponse> create(@AuthenticationPrincipal AuthUser user,
                                                   @Valid @RequestBody DiaryCreateRequest request) {
        return ApiResponse.ok(diaryService.create(user.id(), request));
    }
}
