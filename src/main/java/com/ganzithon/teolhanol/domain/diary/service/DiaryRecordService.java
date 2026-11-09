package com.ganzithon.teolhanol.domain.diary.service;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryDetailResponse;
import com.ganzithon.teolhanol.domain.diary.dto.DiaryListResponse;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기록 조회·삭제 (DRY-02). 모든 조회는 본인 기록만, 남의 기록은 없는 것처럼 404입니다. */
@Service
@RequiredArgsConstructor
public class DiaryRecordService {

    private static final int SUMMARY_LINE_MAX = 60;

    private final DiaryRepository diaryRepository;
    private final CareMessageRepository careMessageRepository;
    private final CareSummaryRepository careSummaryRepository;

    /** month는 yyyy-MM, 비우면 이번 달입니다. 형식이 틀리면 400입니다. */
    @Transactional(readOnly = true)
    public DiaryListResponse list(Long userId, String month) {
        YearMonth target = parseMonth(month);
        List<Diary> diaries = diaryRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, target.atDay(1).atStartOfDay(), target.plusMonths(1).atDay(1).atStartOfDay());

        Map<Long, CareSummary> summaries = careSummaryRepository
                .findByDiaryIdIn(diaries.stream().map(Diary::getId).toList()).stream()
                .collect(Collectors.toMap(s -> s.getDiary().getId(), Function.identity()));

        // 최신순이므로 날짜별로 처음 만나는 기록이 그날의 대표(가장 최근)
        Map<LocalDate, DiaryListResponse.Day> days = new LinkedHashMap<>();
        for (Diary d : diaries) {
            days.putIfAbsent(d.getCreatedAt().toLocalDate(), new DiaryListResponse.Day(
                    d.getCreatedAt().toLocalDate(), d.getId(), d.getEmotion(), d.getEmotion().getLabel()));
        }
        List<DiaryListResponse.Item> items = diaries.stream()
                .map(d -> new DiaryListResponse.Item(d.getId(), d.getCreatedAt(), d.getEmotion(), d.getEmotion().getLabel(),
                        d.getCareType(), d.getCareStatus(), summaryLine(summaries.get(d.getId())),
                        d.getCareStatus() != CareStatus.COMPLETED))
                .toList();
        return new DiaryListResponse(target.toString(), days.values().stream()
                .sorted(java.util.Comparator.comparing(DiaryListResponse.Day::date)).toList(), items);
    }

    @Transactional(readOnly = true)
    public DiaryDetailResponse detail(Long userId, Long diaryId) {
        Diary diary = findOwned(userId, diaryId);
        List<CareMessage> messages = careMessageRepository.findByDiaryIdOrderByIdAsc(diaryId);
        CareSummary summary = careSummaryRepository.findByDiaryId(diaryId).orElse(null);

        String firstMessage = messages.stream().filter(m -> m.getRole() == MessageRole.USER)
                .map(CareMessage::getContent).findFirst().orElse(null);
        boolean completed = diary.getCareStatus() == CareStatus.COMPLETED;
        return new DiaryDetailResponse(diary.getId(), diary.getCreatedAt(), diary.getEmotion(), diary.getEmotion().getLabel(),
                diary.getCareType(), diary.getCareStatus(), firstMessage,
                summary == null ? null : new DiaryDetailResponse.Summary(diary.getCareType(), summary.getSummary(), summary.getCompletedAt()),
                completed && !diary.isCrisisDetected(),
                messages.stream().map(m -> new DiaryDetailResponse.Message(m.getRole(), m.getContent(), m.getCreatedAt())).toList());
    }

    /** 대화와 마무리 카드를 함께 지웁니다. 이미 쌓인 나의 양 털은 그대로 유지합니다. */
    @Transactional
    public void delete(Long userId, Long diaryId) {
        Diary diary = findOwned(userId, diaryId);
        careMessageRepository.deleteByDiaryId(diaryId);
        careSummaryRepository.deleteByDiaryId(diaryId);
        diaryRepository.delete(diary);
    }

    private Diary findOwned(Long userId, Long diaryId) {
        return diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "달은 2026-11 형식으로 보내 주세요.");
        }
    }

    /** 마무리 카드의 첫 줄에서 첫 문장을 목록용 한 줄로 씁니다. 너무 길면 60자에서 줄입니다. */
    private static String summaryLine(CareSummary summary) {
        if (summary == null) {
            return null;
        }
        String line = summary.getSummary().lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        java.util.regex.Matcher sentence = java.util.regex.Pattern.compile("^.+?[.!?](?=\\s|$)").matcher(line);
        if (sentence.find()) {
            line = sentence.group();
        }
        return line.length() > SUMMARY_LINE_MAX ? line.substring(0, SUMMARY_LINE_MAX).stripTrailing() + "…" : line;
    }
}
