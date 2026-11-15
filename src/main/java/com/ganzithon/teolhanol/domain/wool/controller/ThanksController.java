package com.ganzithon.teolhanol.domain.wool.controller;

import com.ganzithon.teolhanol.domain.wool.dto.ThanksResponse;
import com.ganzithon.teolhanol.domain.wool.service.ThanksService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wool", description = "조언(털)")
@RestController
@RequiredArgsConstructor
public class ThanksController {

    private final ThanksService thanksService;

    @Operation(summary = "고마워요 누르기 (WOL-03)",
            description = "그 조언이 달린 양의 주인만 누를 수 있고, 조언마다 한 번입니다. 보상은 없습니다. "
                    + "주인이 아니거나 없는 조언·숨긴 양의 조언은 404, 이미 눌렀으면 409(THANKS_ALREADY_SENT)입니다. "
                    + "졸업한 양의 조언에도 누를 수 있습니다. 성공하면 201 { woolId, thanked: true }.")
    @PostMapping("/wools/{id}/thanks")
    public ResponseEntity<ApiResponse<ThanksResponse>> thank(@AuthenticationPrincipal AuthUser user,
                                                             @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(thanksService.thank(user.id(), id)));
    }
}
