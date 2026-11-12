package com.ganzithon.teolhanol.domain.worry.controller;

import com.ganzithon.teolhanol.domain.worry.dto.WorryPostRequest;
import com.ganzithon.teolhanol.domain.worry.dto.WorryPostResponse;
import com.ganzithon.teolhanol.domain.worry.dto.WorryPostStatus;
import com.ganzithon.teolhanol.domain.worry.service.WorryPostService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Farm", description = "고민 목장")
@RestController
@RequestMapping("/farms")
@RequiredArgsConstructor
public class WorryPostController {

    private final WorryPostService worryPostService;

    @Operation(summary = "고민 올리기 (FRM-04)",
            description = "farmCode는 CAREER / RELATIONSHIP / BURNOUT / SELF(대소문자 무시)이며 ALL로는 올릴 수 없습니다(400). "
                    + "content는 앞뒤 공백 제거 후 1~200자입니다. 하루 3마리까지이며 넘으면 429입니다. "
                    + "결과는 data.status로 구분합니다. CREATED(201): 털 없는 양(1단계)이 태어남, sheepId·farm·stage·stageName·content. "
                    + "CRISIS(200): 양을 만들지 않고 crisisHelp(상담 연결 정보)를 내려주며 24시간 상담 배너가 켜집니다. "
                    + "PII_REJECTED(200): 연락처 같은 개인정보가 있어 저장하지 않았고 message로 수정을 부탁합니다.")
    @PostMapping("/{farmCode}/sheep")
    public ResponseEntity<ApiResponse<WorryPostResponse>> post(@AuthenticationPrincipal AuthUser user,
                                                               @PathVariable String farmCode,
                                                               @RequestBody WorryPostRequest request) {
        WorryPostResponse response = worryPostService.post(user.id(), farmCode, request.content());
        HttpStatus status = response.status() == WorryPostStatus.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }
}
