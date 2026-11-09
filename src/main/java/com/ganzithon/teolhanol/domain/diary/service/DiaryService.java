package com.ganzithon.teolhanol.domain.diary.service;

import com.ganzithon.teolhanol.domain.diary.dto.DiaryCreateRequest;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryCreateResponse;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오늘의 마음 (DRY-01). */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PolicyProperties policy;

    /**
     * 감정을 골라 오늘의 마음을 만듭니다. 하루(서버 시간 0시 기준) 시작 한도를 넘으면 429로 안내합니다.
     * AI 첫 마디는 감정별 고정 문장이라 LLM을 부르지 않습니다.
     */
    @Transactional
    public DiaryCreateResponse create(Long userId, DiaryCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        long today = diaryRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                userId, LocalDate.now().atStartOfDay());
        if (today >= policy.diary().dailyLimit()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        Diary diary = diaryRepository.save(new Diary(user, request.emotion()));
        return new DiaryCreateResponse(diary.getId(), FirstMessages.of(request.emotion()));
    }
}
