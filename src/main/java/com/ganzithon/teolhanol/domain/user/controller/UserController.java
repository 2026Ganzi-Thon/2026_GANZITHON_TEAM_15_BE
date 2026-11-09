package com.ganzithon.teolhanol.domain.user.controller;

import com.ganzithon.teolhanol.domain.user.dto.MeResponse;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @Operation(summary = "내 정보", description = "토큰이 유효한지, 현재 사용자가 누구인지 확인할 때 씁니다.")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.ok(new MeResponse(user.getId(), user.getNickname()));
    }
}
