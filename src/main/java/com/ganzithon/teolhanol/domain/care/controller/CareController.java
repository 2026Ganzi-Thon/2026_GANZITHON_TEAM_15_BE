package com.ganzithon.teolhanol.domain.care.controller;

import com.ganzithon.teolhanol.domain.care.dto.CareStartRequest;
import com.ganzithon.teolhanol.domain.care.dto.CareStartResponse;
import com.ganzithon.teolhanol.domain.care.dto.CareMessageRequest;
import com.ganzithon.teolhanol.domain.care.service.CareChatService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Care", description = "AI 케어 대화")
@RestController
@RequestMapping("/diaries/{diaryId}/care")
@RequiredArgsConstructor
public class CareController {

    private final CareService careService;
    private final CareChatService careChatService;

    @Operation(summary = "케어 대화 보내기 (CARE-02, SAF-01, SAF-02)",
            description = """
                    사용자의 말을 보내면 SSE(text/event-stream)로 응답합니다. 이벤트 순서: progress → (crisis | delta… ) → done.
                    - progress {userMessageCount, ready, maxUserMessages, limitReached}: 3회부터 ready=true, 10회에 닿으면 limitReached
                    - delta {text}: AI 응답 조각
                    - crisis {message, actions[call, continue]}: 위기로 판정되면 AI 응답 대신 전환 메시지. 대화는 계속 보낼 수 있음
                    - notice {message}: 응답이 늦어 다시 시도하는 중. reset은 받은 조각을 지우고 다시 받기, replace {text}는 안전 가드가 문장을 바꿈
                    - error {code, message}: 3번 실패. 말한 내용은 저장되어 있어 다시 보내면 이어갈 수 있음
                    케어 방식을 고르지 않고 바로 보내면 '이야기 들어주기'로 시작합니다. 본인 기록이 아니면 404, 마무리했거나 10회를 넘으면 409입니다.
                    """)
    @PostMapping("/messages")
    public SseEmitter send(@AuthenticationPrincipal AuthUser user, @PathVariable Long diaryId,
                           @Valid @RequestBody CareMessageRequest request) {
        return careChatService.send(user.id(), diaryId, request);
    }

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
