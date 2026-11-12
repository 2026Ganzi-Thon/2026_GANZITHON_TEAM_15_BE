package com.ganzithon.teolhanol.domain.report.dto;

/**
 * 신고 결과. 글쓴이의 user_id·닉네임·본문은 담지 않습니다.
 *
 * @param hidden  이번 신고로 대상이 자동 숨김됐는지
 * @param blocked 글쓴이 차단까지 처리됐는지(이미 차단돼 있던 경우 포함)
 */
public record ReportResponse(Long reportId, boolean hidden, boolean blocked) {
}
