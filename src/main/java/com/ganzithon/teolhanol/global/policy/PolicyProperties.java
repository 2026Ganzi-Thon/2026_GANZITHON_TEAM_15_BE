package com.ganzithon.teolhanol.global.policy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 기능명세서 5장 정책 수치. 시연 기준 초안이므로 application.yml의 app.policy 값만 바꿔 조정합니다.
 */
@Validated
@ConfigurationProperties(prefix = "app.policy")
public record PolicyProperties(
        @Valid Auth auth,
        @Valid Diary diary,
        @Valid Care care,
        @Valid Worry worry,
        @Valid Wool wool,
        @Valid Safety safety
) {

    /** 로그인 잠금: 실패 5회, 5분 */
    public record Auth(@Positive int maxLoginFailures, @Positive int lockMinutes) {
    }

    /** 일기 길이 1~1000자, 하루 최대 3회 */
    public record Diary(@Positive int maxLength, @Positive int dailyLimit) {
    }

    /** 사용자 메시지 최대 10회, AI 응답 300자 이내 권장 */
    public record Care(@Positive int maxUserMessages, @Positive int aiResponseMaxLength) {
    }

    /**
     * 고민 양 정책.
     *
     * @param maxLength                 고민 길이 1~200자
     * @param dailyLimit                하루 고민 양 만들기 3마리
     * @param woolCap                   고민 양 1마리당 털 상한 10개 (최대 단계 도달 시 졸업)
     * @param autoGraduateDays          작성 후 7일 자동 졸업 (P1)
     * @param pageSize                  목장 한 페이지 20마리
     * @param protectedViewMinSentWools '주의' 고민 양은 조언 보낸 이력 5회 이상 사용자에게만 표시
     * @param autoHideReportCount       신고 3회 자동 숨김
     */
    public record Worry(@Positive int maxLength, @Positive int dailyLimit, @Positive int woolCap,
                        @Positive int autoGraduateDays, @Positive int pageSize,
                        @Positive int protectedViewMinSentWools, @Positive int autoHideReportCount) {
    }

    /** 조언 길이 1~100자 */
    public record Wool(@Positive int maxLength) {
    }

    /** 위기 배너 24시간, LLM 분류 타임아웃 5초 */
    public record Safety(@Positive int crisisBannerHours, @Positive int llmTimeoutSeconds) {
    }
}
