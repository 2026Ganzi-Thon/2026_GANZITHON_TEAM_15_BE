package com.ganzithon.teolhanol.domain.farm.service;

import com.ganzithon.teolhanol.domain.farm.dto.FarmResponse;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farmRepository;

    @Transactional(readOnly = true)
    public List<FarmResponse> list() {
        // 보호 양·차단·내 양 제외 같은 사용자별 조건은 풀밭 조회(FarmSheepQueryService)에서 적용합니다.
        return farmRepository.findAllWithWaitingCount();
    }
}
