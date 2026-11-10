package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 규칙 (기능명세서 5장). 가입과 사용 가능 확인 API가 같은 검사를 씁니다.
 * 순서: 길이 → 문자 → 사칭 단어·내용 필터 → 중복.
 */
@Component
@RequiredArgsConstructor
public class NicknameValidator {

    /** 한글 음절·영문·숫자로 된 단어를 공백 한 칸으로만 이어 붙인 형태 */
    private static final Pattern ALLOWED = Pattern.compile("^[가-힣A-Za-z0-9]+( [가-힣A-Za-z0-9]+)*$");

    private final UserRepository userRepository;
    private final PolicyProperties policy;
    private final ObjectProvider<NicknameContentFilter> contentFilters;

    /** 앞뒤 공백을 없앤 저장용 닉네임 */
    public String normalize(String nickname) {
        return nickname == null ? "" : nickname.strip();
    }

    /** 쓸 수 있으면 비어 있고, 쓸 수 없으면 이유를 돌려줍니다. */
    @Transactional(readOnly = true)
    public Optional<NicknameReason> check(String rawNickname) {
        return check(rawNickname, null);
    }

    /**
     * 이미 가입한 사용자가 닉네임을 바꿀 때 쓰는 검사. 중복은 자기 자신을 뺀 다른 사용자만 따지므로
     * 내 이름에서 대소문자만 바꾸는 것도 허용됩니다. currentUserId가 null이면 가입 때와 같습니다.
     */
    @Transactional(readOnly = true)
    public Optional<NicknameReason> check(String rawNickname, Long currentUserId) {
        String nickname = normalize(rawNickname);
        int length = nickname.codePointCount(0, nickname.length());
        PolicyProperties.Auth auth = policy.auth();
        if (length < auth.nicknameMinLength() || length > auth.nicknameMaxLength()) {
            return Optional.of(NicknameReason.LENGTH);
        }
        if (!ALLOWED.matcher(nickname).matches()) {
            return Optional.of(NicknameReason.CHARACTERS);
        }
        if (containsReservedWord(nickname) || contentFilters.orderedStream().anyMatch(f -> !f.isAllowed(nickname))) {
            return Optional.of(NicknameReason.NOT_ALLOWED);
        }
        boolean taken = currentUserId == null
                ? userRepository.existsByNicknameIgnoreCase(nickname)
                : userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, currentUserId);
        if (taken) {
            return Optional.of(NicknameReason.DUPLICATE);
        }
        return Optional.empty();
    }

    public String message(NicknameReason reason) {
        PolicyProperties.Auth auth = policy.auth();
        return reason == NicknameReason.LENGTH
                ? reason.getMessageTemplate().formatted(auth.nicknameMinLength(), auth.nicknameMaxLength())
                : reason.getMessageTemplate();
    }

    private boolean containsReservedWord(String nickname) {
        String compact = compact(nickname);
        return policy.auth().reservedNicknameWords().stream()
                .map(NicknameValidator::compact)
                .anyMatch(compact::contains);
    }

    private static String compact(String value) {
        return value.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
