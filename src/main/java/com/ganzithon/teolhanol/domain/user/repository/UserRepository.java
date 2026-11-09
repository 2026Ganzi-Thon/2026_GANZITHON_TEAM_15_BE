package com.ganzithon.teolhanol.domain.user.repository;

import com.ganzithon.teolhanol.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNicknameIgnoreCase(String nickname);

    Optional<User> findByEmail(String email);
}
