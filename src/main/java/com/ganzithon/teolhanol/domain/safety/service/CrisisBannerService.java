package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.dto.CrisisBannerResponse;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrisisBannerService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CrisisBannerResponse get(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        LocalDateTime until = user.getCrisisBannerUntil();
        boolean active = until != null && until.isAfter(LocalDateTime.now());
        return new CrisisBannerResponse(active, active ? until : null);
    }
}
