package com.ganzithon.teolhanol.domain.report.controller;

import com.ganzithon.teolhanol.domain.report.dto.ReportCreateRequest;
import com.ganzithon.teolhanol.domain.report.dto.ReportResponse;
import com.ganzithon.teolhanol.domain.report.service.ReportService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "신고")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "고민 양·조언 신고 (RPT-01)",
            description = "targetType은 WORRY_SHEEP(볼 수 있는 다른 목동의 양) 또는 WOOL(내 양에 달린 조언)이고 "
                    + "targetId는 그 양·조언의 id입니다. reason은 ABUSIVE(악성 표현)·PERSONAL_INFO(개인정보)·SPAM(홍보·스팸)·"
                    + "LOOKS_DANGEROUS(글쓴이가 위험해 보여요)·ETC(기타)입니다. "
                    + "서로 다른 목동의 신고가 3회 쌓이면 대상을 자동으로 숨기고, 이번 신고로 숨겨졌으면 hidden=true입니다. "
                    + "LOOKS_DANGEROUS는 1회만으로 글쓴이에게 상담 안내 배너를 켭니다. "
                    + "blockUser=true면 글쓴이를 함께 차단하고 blocked=true입니다(이미 차단했어도 true). "
                    + "내 글이면 400(REPORT_OWN_CONTENT), 이미 신고한 대상이면 409(REPORT_DUPLICATED), "
                    + "없거나 볼 수 없는 대상(숨김·차단 관계·볼 수 없는 보호 양·다른 목동의 졸업 양·내가 받지 않은 조언)은 404입니다. "
                    + "글쓴이의 user_id는 받지도 내보내지도 않습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> report(@AuthenticationPrincipal AuthUser user,
                                                              @RequestBody ReportCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reportService.report(user.id(), request)));
    }
}
