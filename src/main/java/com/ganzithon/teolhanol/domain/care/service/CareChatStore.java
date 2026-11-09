package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.llm.LlmMessage;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 케어 대화 한 턴의 DB 작업. LLM 호출 전후로 짧은 트랜잭션을 나눠 쓰기 위해 별도 빈으로 둡니다. */
@Component
@RequiredArgsConstructor
class CareChatStore {

    /** 받은 한 턴의 상태 */
    record Turn(Long diaryId, Emotion emotion, CareType careType, int userMessageCount, boolean afterCrisis,
                boolean ready, boolean limitReached, int maxUserMessages) {
    }

    private final DiaryRepository diaryRepository;
    private final CareMessageRepository careMessageRepository;
    private final PolicyProperties policy;

    /**
     * 사용자 메시지를 검증하고 저장합니다. 본인 기록이 아니면 404, 이미 마무리했거나 메시지 한도에 닿았으면 409입니다.
     * 케어 방식을 고르지 않고 바로 말을 적은 경우 '이야기 들어주기'로 시작합니다.
     */
    @Transactional
    public Turn begin(Long userId, Long diaryId, String content) {
        Diary diary = diaryRepository.findByIdAndUserIdForUpdate(diaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (diary.getCareStatus() == CareStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 마무리한 마음이에요.");
        }
        if (diary.getUserMessageCount() >= policy.care().maxUserMessages()) {
            throw new BusinessException(ErrorCode.CONFLICT, "충분히 이야기를 나눴어요. 이제 털 한 올을 쌓고 마무리해 볼까요?");
        }
        if (!diary.isCareStarted()) {
            diary.startCare(CareType.LISTEN);
        }
        boolean afterCrisis = careMessageRepository.findFirstByDiaryIdOrderByIdDesc(diaryId)
                .map(last -> last.getRole() == MessageRole.AI && CareChatService.CRISIS_MESSAGE.equals(last.getContent()))
                .orElse(false);

        careMessageRepository.save(new CareMessage(diary, MessageRole.USER, content));
        int count = diary.addUserMessage();
        return new Turn(diary.getId(), diary.getEmotion(), diary.getCareType(), count, afterCrisis,
                count >= policy.care().minUserMessagesForWool(), count >= policy.care().maxUserMessages(), policy.care().maxUserMessages());
    }

    @Transactional
    public void applyRisk(Long diaryId, RiskLevel level) {
        diaryRepository.findById(diaryId).ifPresent(diary -> diary.applyRisk(level));
    }

    @Transactional
    public void saveAiMessage(Long diaryId, String content) {
        careMessageRepository.save(new CareMessage(diaryRepository.getReferenceById(diaryId), MessageRole.AI, content));
    }

    @Transactional(readOnly = true)
    public List<LlmMessage> history(Long diaryId) {
        return careMessageRepository.findByDiaryIdOrderByIdAsc(diaryId).stream()
                .map(m -> m.getRole() == MessageRole.USER ? LlmMessage.user(m.getContent()) : LlmMessage.assistant(m.getContent()))
                .toList();
    }
}
