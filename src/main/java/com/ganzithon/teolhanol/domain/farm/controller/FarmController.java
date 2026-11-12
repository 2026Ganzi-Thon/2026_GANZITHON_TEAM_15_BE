package com.ganzithon.teolhanol.domain.farm.controller;

import com.ganzithon.teolhanol.domain.farm.dto.FarmResponse;
import com.ganzithon.teolhanol.domain.farm.service.FarmService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Farm", description = "고민 목장")
@RestController
@RequestMapping("/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @Operation(summary = "목장 목록 (FRM-01)",
            description = "목장 4곳(진로·관계·번아웃·나)을 순서대로, 목장별 털을 기다리는 양 수와 함께 돌려줍니다. "
                    + "숨김·졸업한 양은 세지 않으며 인기 순위나 조회수는 없습니다.")
    @GetMapping
    public ApiResponse<List<FarmResponse>> list() {
        return ApiResponse.ok(farmService.list());
    }
}
