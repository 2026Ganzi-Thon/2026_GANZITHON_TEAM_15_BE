package com.ganzithon.teolhanol.domain.wool.controller;

import com.ganzithon.teolhanol.domain.wool.dto.WoolSendRequest;
import com.ganzithon.teolhanol.domain.wool.dto.WoolSendResponse;
import com.ganzithon.teolhanol.domain.wool.dto.WoolSendStatus;
import com.ganzithon.teolhanol.domain.wool.service.WoolSendService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wool", description = "조언(털)")
@RestController
@RequiredArgsConstructor
public class WoolSendController {

    private final WoolSendService woolSendService;

    @Operation(summary = "조언(털) 보내기 (WOL-01, SAF-04, SAF-05)",
            description = "content는 앞뒤 공백 제거 후 1~100자입니다(400). "
                    + "내 양이면 403(WOOL_OWN_SHEEP), 이미 보냈으면 409(WOOL_ALREADY_SENT), "
                    + "그 사이 졸업한 양이면 409(WORRY_SHEEP_GRADUATED), 숨김·차단·볼 수 없는 보호 양·없는 양은 404입니다. "
                    + "결과는 data.status로 구분합니다. "
                    + "SENT(201): 저장됨. woolId, sheepStage·sheepStageName(양의 새 단계), stageUp(단계가 올랐는지), "
                    + "graduated(털이 다 차서 졸업했는지), yarnAdded(받은 털실), yarnCount(내 털실). "
                    + "SUGGEST(200): 어조 주의로 저장하지 않음. category·categoryLabel·message와 바꿀 문장 suggestion"
                    + "(만들지 못했으면 빠짐). sendAsIs=true로 다시 보내면 그대로 저장합니다. "
                    + "BLOCKED(200): 금칙어(BANNED_WORD)·개인정보(PII)로 저장하지 않음. category·message. "
                    + "어조 확인 AI가 응답하지 않으면 금칙어 판정만으로 보냅니다.")
    @PostMapping("/worry-sheep/{id}/wools")
    public ResponseEntity<ApiResponse<WoolSendResponse>> send(@AuthenticationPrincipal AuthUser user,
                                                              @PathVariable Long id,
                                                              @RequestBody WoolSendRequest request) {
        WoolSendResponse response = woolSendService.send(user.id(), id, request.content(), request.sendAsIs());
        HttpStatus status = response.status() == WoolSendStatus.SENT ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }
}
