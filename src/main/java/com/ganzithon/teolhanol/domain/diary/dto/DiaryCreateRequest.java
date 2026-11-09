package com.ganzithon.teolhanol.domain.diary.dto;

import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 오늘의 마음 만들기 요청 (DRY-01). 일기 본문과 감정 강도는 받지 않습니다. */
public record DiaryCreateRequest(
        @Schema(description = "JOY / CALM / TIRED / ANXIOUS / DEPRESSED / LONELY / ANGRY / COMPLICATED", example = "TIRED")
        @NotNull(message = "감정을 골라 주세요.")
        Emotion emotion
) {
}
