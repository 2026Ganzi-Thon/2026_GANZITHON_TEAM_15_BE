package com.ganzithon.teolhanol.domain.notification.repository;

import com.ganzithon.teolhanol.domain.notification.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserId(Long userId);

    /** 본인 구독만 지웁니다. 지운 개수(0 또는 1) */
    @Transactional
    @Modifying
    @Query("delete from PushSubscription p where p.endpoint = :endpoint and p.user.id = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);
}
