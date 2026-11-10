package com.ganzithon.teolhanol.domain.user.repository;

import com.ganzithon.teolhanol.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNicknameIgnoreCase(String nickname);

    /** 내 자신을 뺀 다른 사용자가 같은 닉네임을 쓰는지 (닉네임 바꾸기용) */
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

    Optional<User> findByEmail(String email);

    /** 털실처럼 동시에 바뀌면 안 되는 값을 다룰 때 쓰는 잠금 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
