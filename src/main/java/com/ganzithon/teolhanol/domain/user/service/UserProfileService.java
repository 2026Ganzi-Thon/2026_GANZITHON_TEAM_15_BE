package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.user.dto.MeResponse;
import com.ganzithon.teolhanol.domain.user.dto.NicknameChangeRequest;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 수정 (ACC-05 닉네임 바꾸기). */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final NicknameValidator nicknameValidator;

    /**
     * 가입 때와 같은 규칙(길이·문자·사칭 단어·내용 필터·중복)으로 검사한 뒤 닉네임을 바꿉니다.
     * 지금 쓰는 닉네임과 똑같이 보내면 아무것도 바꾸지 않고 성공합니다.
     * 이미 올린 고민과 조언에는 사용자 id로 연결되어 있어 새 닉네임이 그대로 보입니다.
     */
    @Transactional
    public MeResponse changeNickname(Long userId, NicknameChangeRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String nickname = nicknameValidator.normalize(request.nickname());
        if (nickname.equals(user.getNickname())) {
            return new MeResponse(user.getId(), user.getNickname());
        }

        nicknameValidator.check(nickname, userId).ifPresent(reason -> {
            throw new BusinessException(ErrorCode.NICKNAME_UNAVAILABLE, nicknameValidator.message(reason));
        });
        user.changeNickname(nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이름으로 바꾸려 해 유니크 제약에 걸린 경우
            throw new BusinessException(ErrorCode.NICKNAME_UNAVAILABLE, nicknameValidator.message(NicknameReason.DUPLICATE));
        }
        return new MeResponse(user.getId(), user.getNickname());
    }
}
