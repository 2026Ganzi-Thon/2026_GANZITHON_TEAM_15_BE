package com.ganzithon.teolhanol.domain.farm.service;

import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.farm.dto.FarmRecommendationResponse;
import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FarmRecommendationService {

    private final DiaryRepository diaryRepository;
    private final FarmRepository farmRepository;
    private final FarmRecommender farmRecommender;

    @Transactional(readOnly = true)
    public FarmRecommendationResponse recommend(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String code = farmRecommender.recommend(diary.getEmotion(), diary.getCareType());
        Farm farm = farmRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
        return new FarmRecommendationResponse(farm.getCode(), farm.getName(), farm.getTabName());
    }
}
