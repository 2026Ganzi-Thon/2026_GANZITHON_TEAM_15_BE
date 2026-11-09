package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.user.dto.NicknameAvailabilityResponse;
import com.ganzithon.teolhanol.domain.user.dto.SignupRequest;
import com.ganzithon.teolhanol.domain.user.dto.SignupResponse;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
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
                registered.sheep().getStage(), registered.sheep().getWoolCount());
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
