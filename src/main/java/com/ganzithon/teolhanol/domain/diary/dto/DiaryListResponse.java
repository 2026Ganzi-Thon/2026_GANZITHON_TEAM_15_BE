package com.ganzithon.teolhanol.domain.diary.dto;

import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 달 기록 (REC-01).
 *
 * @param month 조회한 달 (yyyy-MM)
 * @param days  날짜별 대표 감정. 하루에 여러 개면 가장 최근 것. 달력에 감정을 표시할 때 사용
 * @param items 그 달의 기록 목록, 최신순
 */
public record DiaryListResponse(String month, List<Day> days, List<Item> items) {

    public record Day(LocalDate date, Long diaryId, Emotion emotion, String emotionLabel) {
    }

    /**
     * 목록 항목.
     *
     * @param summaryLine 오늘 받은 말 한 줄. 마무리 전이면 null
     * @param canResume   마무리 전(대화 중이거나 케어 방식을 아직 고르지 않음)이라 이어서 이야기할 수 있는지.
     *                    목록에 '이어서 이야기하기'를 보여줄지는 프론트와 협의
     */
    public record Item(Long diaryId, LocalDateTime createdAt, Emotion emotion, String emotionLabel,
                       CareType careType, CareStatus careStatus, String summaryLine, boolean canResume) {
    }
}
