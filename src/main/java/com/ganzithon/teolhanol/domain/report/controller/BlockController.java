package com.ganzithon.teolhanol.domain.report.controller;

import com.ganzithon.teolhanol.domain.report.dto.BlockCreateRequest;
import com.ganzithon.teolhanol.domain.report.dto.BlockResponse;
import com.ganzithon.teolhanol.domain.report.service.BlockService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Block", description = "사용자 차단")
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @Operation(summary = "목동 차단 (RPT-02)",
            description = "targetType은 WORRY_SHEEP(볼 수 있는 양의 주인) 또는 WOOL(내 양에 달린 조언의 보낸 사람)이고 "
                    + "targetId는 그 양·조언의 id입니다. user_id는 받지도 내보내지도 않습니다. "
                    + "새 차단은 201, 이미 차단한 목동이면 기존 차단을 200으로 돌려줍니다. 자기 자신은 400, 대상이 없으면 404입니다. "
                    + "차단하면 서로의 양이 목장에서 보이지 않습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<BlockResponse>> block(@AuthenticationPrincipal AuthUser user,
                                                            @RequestBody BlockCreateRequest request) {
        BlockService.Result result = blockService.block(user.id(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(BlockResponse.from(result.block())));
    }

    @Operation(summary = "차단한 목동 목록 (RPT-02)", description = "닉네임과 차단일을 최신순으로 돌려줍니다.")
    @GetMapping
    public ApiResponse<List<BlockResponse>> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(blockService.list(user.id()));
    }

    @Operation(summary = "차단 해제 (RPT-02)", description = "본인이 만든 차단만 해제할 수 있고, 아니면 404입니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        blockService.unblock(user.id(), id);
        return ApiResponse.ok();
    }
}
