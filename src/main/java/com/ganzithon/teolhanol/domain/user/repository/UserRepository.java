package com.ganzithon.teolhanol.domain.user.repository;

import com.ganzithon.teolhanol.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
