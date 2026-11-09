package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.user.dto.LoginRequest;
import com.ganzithon.teolhanol.domain.user.dto.LoginResponse;
import com.ganzithon.teolhanol.domain.user.dto.NicknameAvailabilityResponse;
import com.ganzithon.teolhanol.domain.user.dto.SignupRequest;
import com.ganzithon.teolhanol.domain.user.dto.SignupResponse;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.security.JwtProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원 가입과 닉네임 확인 (ACC-01).
 * 저장은 {@link UserRegistrar}의 트랜잭션에서 하고, 동시에 같은 이메일·닉네임으로 가입해 유니크 제약에 걸리면
 * 트랜잭션 밖에서 다시 확인해 알맞은 오류로 바꿉니다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final NicknameValidator nicknameValidator;
    private final UserRegistrar userRegistrar;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PolicyProperties policy;
    private final JwtProvider jwtProvider;
    private final LoginAttempts loginAttempts;

    public NicknameAvailabilityResponse checkNickname(String nickname) {
        return nicknameValidator.check(nickname)
                .map(reason -> new NicknameAvailabilityResponse(false, reason, nicknameValidator.message(reason)))
                .orElseGet(NicknameAvailabilityResponse::ok);
    }

    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String nickname = nicknameValidator.normalize(request.nickname());

        if (request.password().length() < policy.auth().passwordMinLength()) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT,
                    "비밀번호는 %d자 이상으로 정해 주세요.".formatted(policy.auth().passwordMinLength()));
        }
        nicknameValidator.check(nickname).ifPresent(reason -> {
            throw new BusinessException(ErrorCode.NICKNAME_UNAVAILABLE, nicknameValidator.message(reason));
        });
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        UserRegistrar.Registered registered;
        try {
            registered = userRegistrar.register(email, passwordEncoder.encode(request.password()), nickname);
        } catch (DataIntegrityViolationException e) {
            if (userRepository.existsByEmail(email)) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
            throw new BusinessException(ErrorCode.NICKNAME_UNAVAILABLE,
                    nicknameValidator.message(NicknameReason.DUPLICATE));
        }
        return new SignupResponse(registered.user().getId(), registered.user().getNickname(),
                registered.sheep().getStage(), registered.sheep().getWoolCount(),
                jwtProvider.issue(registered.user().getId()), jwtProvider.expiresInSeconds());
    }

    /**
     * 이메일·비밀번호 로그인 (ACC-02). 이메일이 없는지 비밀번호가 틀렸는지는 알려주지 않고,
     * 연속 실패가 정책 횟수에 닿으면 정책 시간 동안 잠급니다. 잠금 중에는 비밀번호가 맞아도 들어올 수 없습니다.
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        LocalDateTime now = LocalDateTime.now();
        if (user.isLocked(now)) {
            throw lockedException(user.getLockedUntil(), now);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LocalDateTime lockedUntil = loginAttempts.recordFailure(user.getId());
            if (lockedUntil != null) {
                throw lockedException(lockedUntil, now);
            }
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getLoginFailCount() > 0 || user.getLockedUntil() != null) {
            loginAttempts.clear(user.getId());
        }
        return new LoginResponse(user.getId(), user.getNickname(),
                jwtProvider.issue(user.getId()), jwtProvider.expiresInSeconds());
    }

    private static BusinessException lockedException(LocalDateTime lockedUntil, LocalDateTime now) {
        long seconds = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
        long minutes = (seconds + 59) / 60;
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                "비밀번호를 여러 번 다시 입력하셨어요. 마음을 잠시 쉬었다가, %d분 뒤에 다시 로그인해 주세요.".formatted(minutes));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
