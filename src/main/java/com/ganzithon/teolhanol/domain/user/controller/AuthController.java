package com.ganzithon.teolhanol.domain.user.controller;

import com.ganzithon.teolhanol.domain.user.dto.NicknameAvailabilityResponse;
import com.ganzithon.teolhanol.domain.user.dto.SignupRequest;
import com.ganzithon.teolhanol.domain.user.dto.SignupResponse;
import com.ganzithon.teolhanol.domain.user.service.AuthService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원 가입·로그인")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "닉네임 사용 가능 여부 (ACC-01)",
            description = "가입 화면에서 입력할 때 확인합니다. reason: LENGTH / CHARACTERS / DUPLICATE / NOT_ALLOWED")
    @GetMapping("/nickname/availability")
    public ApiResponse<NicknameAvailabilityResponse> nicknameAvailability(@RequestParam String nickname) {
        return ApiResponse.ok(authService.checkNickname(nickname));
    }

    @Operation(summary = "회원 가입 (ACC-01)", description = "이메일·비밀번호·닉네임으로 가입하고 나의 양(1단계, 털 0)을 함께 만듭니다.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }
}
