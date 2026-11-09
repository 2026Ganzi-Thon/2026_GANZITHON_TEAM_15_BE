package com.ganzithon.teolhanol.domain.diary.dto;

import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기록 상세 (REC-02).
 *
 * @param firstMessage 내가 한 이야기 (첫 사용자 메시지). 아직 말하지 않았으면 null
 * @param summary      마무리 카드(오늘 받은 말). 마무리 전이면 null
 * @param woolEarned   이 마음으로 털 한 올을 받았는지 (마무리했고 위기 신호가 없었던 경우)
 * @param messages     대화 전체, 오래된 순
 */
public record DiaryDetailResponse(Long diaryId, LocalDateTime createdAt, Emotion emotion, String emotionLabel,
                                  CareType careType, CareStatus careStatus, String firstMessage,
                                  Summary summary, boolean woolEarned, List<Message> messages) {

    public record Summary(CareType careType, String text, LocalDateTime completedAt) {
    }

    public record Message(MessageRole role, String content, LocalDateTime createdAt) {
    }
}
