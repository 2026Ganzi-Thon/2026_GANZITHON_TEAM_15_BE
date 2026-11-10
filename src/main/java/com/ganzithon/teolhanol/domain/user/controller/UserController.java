package com.ganzithon.teolhanol.domain.user.controller;

import com.ganzithon.teolhanol.domain.user.dto.MeResponse;
import com.ganzithon.teolhanol.domain.user.dto.NicknameChangeRequest;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.user.service.UserProfileService;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

    @Operation(summary = "내 정보", description = "토큰이 유효한지, 현재 사용자가 누구인지 확인할 때 씁니다.")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.ok(new MeResponse(user.getId(), user.getNickname()));
    }

    @Operation(summary = "닉네임 바꾸기 (ACC-05 ME-02)",
            description = "가입 때와 같은 규칙(2~10자, 한글·영문·숫자·띄어쓰기 한 칸, 중복·금칙어·사칭 단어 불가)으로 바꿉니다. "
                    + "지금 닉네임과 같으면 그대로 성공하고, 쓸 수 없으면 409(ACCOUNT_NICKNAME_UNAVAILABLE)와 이유 문구를 줍니다.")
    @PutMapping("/me/nickname")
    public ApiResponse<MeResponse> changeNickname(@AuthenticationPrincipal AuthUser authUser,
                                                  @Valid @RequestBody NicknameChangeRequest request) {
        return ApiResponse.ok(userProfileService.changeNickname(authUser.id(), request));
    }
}
