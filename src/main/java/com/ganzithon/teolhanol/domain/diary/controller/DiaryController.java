package com.ganzithon.teolhanol.domain.diary.controller;

import com.ganzithon.teolhanol.domain.diary.dto.DiaryCreateRequest;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryCreateResponse;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryDetailResponse;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryListResponse;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryMonthSummaryResponse;
import com.ganzithon.teolhanol.domain.diary.service.DiaryRecordService;
import com.ganzithon.teolhanol.domain.diary.service.DiaryService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final DiaryRecordService diaryRecordService;

    @Operation(summary = "오늘 마음 고르기 (DRY-01)",
            description = "감정 8종 중 하나로 오늘의 마음 기록을 만들고 AI 첫 마디를 돌려줍니다. 하루 3회까지이며 넘으면 429입니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiaryCreateResponse> create(@AuthenticationPrincipal AuthUser user,
                                                   @Valid @RequestBody DiaryCreateRequest request) {
        return ApiResponse.ok(diaryService.create(user.id(), request));
    }

    @Operation(summary = "기록 목록 (DRY-02 REC-01)",
            description = "month=yyyy-MM(비우면 이번 달). days는 날짜별 대표 감정(하루 여러 개면 가장 최근), items는 최신순 목록입니다. "
                    + "summaryLine은 오늘 받은 말 한 줄(마무리 전이면 null), canResume은 마무리 전이라 이어서 이야기할 수 있는지입니다.")
    @GetMapping
    public ApiResponse<DiaryListResponse> list(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) String month) {
        return ApiResponse.ok(diaryRecordService.list(user.id(), month));
    }

    @Operation(summary = "이번 달 한 줄 돌아보기 (REV-02)",
            description = "month=yyyy-MM(비우면 이번 달). woolCount는 그 달에 털을 받은 마음의 수, sentence는 가장 자주 고른 "
                    + "'감정 × 케어 방식' 조합 한 문장입니다(예: 불안한 날엔 위로받기를 자주 골랐어요). 3개 미만이면 sentence는 null입니다.")
    @GetMapping("/summary")
    public ApiResponse<DiaryMonthSummaryResponse> monthSummary(@AuthenticationPrincipal AuthUser user,
                                                               @RequestParam(required = false) String month) {
        return ApiResponse.ok(diaryRecordService.monthSummary(user.id(), month));
    }

    @Operation(summary = "기록 상세 (DRY-02 REC-02)",
            description = "감정, 케어 방식, 마무리 카드, 내가 한 이야기(첫 메시지), 대화 전체. 남의 기록은 404입니다.")
    @GetMapping("/{diaryId}")
    public ApiResponse<DiaryDetailResponse> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long diaryId) {
        return ApiResponse.ok(diaryRecordService.detail(user.id(), diaryId));
    }

    @Operation(summary = "기록 삭제 (DRY-02)",
            description = "대화와 마무리 카드를 함께 지웁니다. 수정은 없습니다. 이미 쌓인 나의 양 털은 그대로 유지됩니다.")
    @DeleteMapping("/{diaryId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long diaryId) {
        diaryRecordService.delete(user.id(), diaryId);
        return ApiResponse.ok();
    }
}
