package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 케어 시작 상태 저장. LLM 호출 전후의 짧은 트랜잭션으로 나눠 DB 잠금을 오래 잡지 않습니다. */
@Component
@RequiredArgsConstructor
class CareStarter {

    private final DiaryRepository diaryRepository;
    private final CareMessageRepository careMessageRepository;

    /** 본인의 아직 시작하지 않은 기록만 IN_PROGRESS로 바꾸고 감정을 돌려줍니다. */
    @Transactional
    public Emotion begin(Long userId, Long diaryId, CareType careType) {
        Diary diary = diaryRepository.findByIdAndUserIdForUpdate(diaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (diary.isCareStarted()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이 마음은 이미 돌보기를 시작했어요.");
        }
        diary.startCare(careType);
        return diary.getEmotion();
    }

    @Transactional
    public void saveAiMessage(Long diaryId, String content) {
        careMessageRepository.save(new CareMessage(diaryRepository.getReferenceById(diaryId), MessageRole.AI, content));
    }
}
