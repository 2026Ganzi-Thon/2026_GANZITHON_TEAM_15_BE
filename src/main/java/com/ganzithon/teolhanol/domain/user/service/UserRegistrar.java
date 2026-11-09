package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 회원과 나의 양(1단계, 털 0)을 한 트랜잭션으로 만듭니다. */
@Component
@RequiredArgsConstructor
class UserRegistrar {

    private final UserRepository userRepository;
    private final MySheepRepository mySheepRepository;

    @Transactional
    public Registered register(String email, String passwordHash, String nickname) {
        User user = userRepository.saveAndFlush(new User(email, passwordHash, nickname));
        MySheep sheep = mySheepRepository.saveAndFlush(new MySheep(user));
        return new Registered(user, sheep);
    }

    record Registered(User user, MySheep sheep) {
    }
}
