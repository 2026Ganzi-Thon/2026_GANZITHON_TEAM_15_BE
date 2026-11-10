package com.ganzithon.teolhanol.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.user.service.NicknameReason;
import com.ganzithon.teolhanol.domain.user.service.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NicknameValidatorTest {

    @Autowired NicknameValidator validator;
    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;

    @BeforeEach
    void setUp() {
        mySheepRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("taken@test.com", "hash", "Sleepy Ram"));
    }

    @ParameterizedTest(name = "\"{0}\" → 사용 가능")
    @CsvSource({"다정한 목동", "양", "sheep 01", "털복숭이123", "가나다라마바사아자차", "'  앞뒤 공백  '"})
    void 규칙에_맞는_닉네임은_쓸_수_있다(String nickname) {
        String value = nickname.length() == 1 ? nickname + "a" : nickname;
        assertThat(validator.check(value)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "가, LENGTH",
            "열세글자를넘는아주긴닉네임이다, LENGTH",
            "가나다라마바사아자차카, LENGTH",
            "목동!, CHARACTERS",
            "'다정한  목동', CHARACTERS",
            "ㄱㄴㄷ, CHARACTERS",
            "운영자 목동, NOT_ALLOWED",
            "털 한 올, NOT_ALLOWED",
            "ADMIN01, NOT_ALLOWED",
            "sleepy ram, DUPLICATE"
    })
    void 규칙에_어긋나면_이유를_돌려준다(String nickname, NicknameReason reason) {
        assertThat(validator.check(nickname)).contains(reason);
    }

    @Test
    void 길이_안내_문구에_정책값이_들어간다() {
        assertThat(validator.message(NicknameReason.LENGTH)).isEqualTo("닉네임은 2~10자로 정해 주세요.");
    }
}
