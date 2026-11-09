package com.ganzithon.teolhanol.domain.care.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 케어 대화에서 사용자가 보내는 말 (CARE-02) */
public record CareMessageRequest(
        @Schema(example = "오늘 발표를 망친 것 같아서 계속 생각나요")
        @NotBlank(message = "이야기를 적어 주세요.")
        @Size(max = 1000, message = "한 번에 1000자까지 적을 수 있어요.")
        String content
) {
}
