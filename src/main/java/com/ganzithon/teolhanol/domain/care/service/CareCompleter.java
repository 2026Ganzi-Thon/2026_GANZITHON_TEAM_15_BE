package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.care.dto.CareCompleteResponse;
import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import com.ganzithon.teolhanol.global.policy.SheepStage;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 마무리 카드 저장, 케어 완료 표시, 털 +1을 한 트랜잭션으로 처리합니다. */
@Component
@RequiredArgsConstructor
class CareCompleter {

    private final DiaryRepository diaryRepository;
    private final CareSummaryRepository careSummaryRepository;
    private final MySheepRepository mySheepRepository;
    private final PolicyProperties policy;

    /** LLM 호출 전에 상태만 확인합니다. 이미 마무리했다면 저장된 결과를 돌려줍니다(중복 요청 안전). */
    @Transactional(readOnly = true)
    public Check check(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (diary.getCareStatus() == CareStatus.COMPLETED) {
            return Check.done(alreadyDone(diary));
        }
        requireEnoughMessages(diary);
        return Check.ready(diary.getCareType(), diary.getEmotion().getLabel());
    }

    /**
     * 카드를 저장하고 털을 쌓습니다. 털은 사용자 메시지가 최소 횟수 이상이고 위기 신호가 없었던 대화에만 줍니다.
     * 동시에 두 번 들어오면 먼저 잠근 쪽만 반영하고 나머지는 저장된 결과를 돌려줍니다.
     */
    @Transactional
    public CareCompleteResponse complete(Long userId, Long diaryId, String cardText) {
        Diary diary = diaryRepository.findByIdAndUserIdForUpdate(diaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (diary.getCareStatus() == CareStatus.COMPLETED) {
            return alreadyDone(diary);
        }
        requireEnoughMessages(diary);

        careSummaryRepository.save(new CareSummary(diary, cardText, LocalDateTime.now()));
        diary.completeCare();

        MySheep sheep = mySheepRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        int stageBefore = SheepStage.ofMySheep(sheep.getWoolCount()).getLevel();
        int added = diary.isCrisisDetected() ? 0 : 1;
        if (added == 1) {
            sheep.addWool();
        }
        int stageAfter = SheepStage.ofMySheep(sheep.getWoolCount()).getLevel();
        return response(diary, cardText, added, sheep.getWoolCount(), stageAfter, stageAfter > stageBefore);
    }

    private void requireEnoughMessages(Diary diary) {
        if (!diary.isCareStarted() || diary.getUserMessageCount() < policy.care().minUserMessagesForWool()) {
            throw new BusinessException(ErrorCode.CONFLICT, "털 한 올을 쌓으려면 이야기를 조금 더 나눠야 해요.");
        }
    }

    private CareCompleteResponse alreadyDone(Diary diary) {
        String text = careSummaryRepository.findByDiaryId(diary.getId()).map(CareSummary::getSummary).orElse("");
        MySheep sheep = mySheepRepository.findByUserId(diary.getUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return response(diary, text, 0, sheep.getWoolCount(), SheepStage.ofMySheep(sheep.getWoolCount()).getLevel(), false);
    }

    private static CareCompleteResponse response(Diary diary, String text, int added, int total, int stage, boolean stageUp) {
        return new CareCompleteResponse(new CareCompleteResponse.Summary(diary.getCareType(), text),
                new CareCompleteResponse.Wool(added, total, stage, stageUp));
    }

    /** check 결과: 이미 끝났으면 result, 아니면 카드를 만들 정보 */
    record Check(CareCompleteResponse result, com.ganzithon.teolhanol.domain.diary.entity.CareType careType, String emotionLabel) {

        static Check done(CareCompleteResponse result) {
            return new Check(result, null, null);
        }

        static Check ready(com.ganzithon.teolhanol.domain.diary.entity.CareType careType, String emotionLabel) {
            return new Check(null, careType, emotionLabel);
        }

        boolean isDone() {
            return result != null;
        }
    }
}
