package com.ganzithon.teolhanol.domain.user.controller;

import com.ganzithon.teolhanol.domain.user.dto.RecordsDeleteResponse;
import com.ganzithon.teolhanol.domain.user.service.AccountService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "내 계정 (기록 전체 삭제, 탈퇴)")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final AccountService accountService;

    @Operation(summary = "기록 전체 삭제 (ACC-03)",
            description = "탈퇴하지 않고 마음 돌보기 기록(오늘의 마음·대화·마무리 카드)만 모두 지웁니다. 되돌릴 수 없습니다. "
                    + "이미 쌓인 나의 양 털과 털실, 아이템은 유지됩니다.")
    @DeleteMapping("/records")
    public ApiResponse<RecordsDeleteResponse> deleteRecords(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(accountService.deleteRecords(user.id()));
    }

    @Operation(summary = "탈퇴 (ACC-03)",
            description = "대화, 나의 양, 고민 양과 거기 달린 조언, 보낸 조언, 신고·차단 등 모든 데이터를 즉시 지우고 계정을 삭제합니다. "
                    + "되돌릴 수 없습니다. 같은 이메일로 다시 가입할 수 있고 새 계정으로 시작합니다.")
    @DeleteMapping
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal AuthUser user) {
        accountService.deleteAccount(user.id());
        return ApiResponse.ok();
    }
}
