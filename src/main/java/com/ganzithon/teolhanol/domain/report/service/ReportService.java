package com.ganzithon.teolhanol.domain.report.service;

import com.ganzithon.teolhanol.domain.report.dto.ReportCreateRequest;
import com.ganzithon.teolhanol.domain.report.dto.ReportResponse;
import com.ganzithon.teolhanol.domain.report.entity.Report;
import com.ganzithon.teolhanol.domain.report.entity.ReportReason;
import com.ganzithon.teolhanol.domain.report.entity.ReportTargetType;
import com.ganzithon.teolhanol.domain.report.repository.ReportRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.domain.worry.service.WorrySheepAccess;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.policy.PolicyProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고민 양·조언 신고 (RPT-01).
 * <ul>
 *   <li>대상 행(양 또는 조언)을 먼저 잠그므로, 같은 대상에 대한 신고는 차례로 처리되어 누적 숨김이 정확합니다.
 *       (MySQL REPEATABLE READ 에서도 잠금 뒤 첫 일반 조회 시점에 스냅샷이 잡히므로 앞선 신고가 보입니다.)</li>
 *   <li>서로 다른 신고자의 신고가 app.policy.worry.auto-hide-report-count 회 쌓이면 대상을 숨깁니다.
 *       조언을 숨겨도 양에 이미 쌓인 털 수는 줄이지 않습니다.</li>
 *   <li>'위험해 보여요'는 1회만으로 글쓴이에게 상담 안내 배너를 켭니다(누적 숨김에도 1회로 셉니다).</li>
 *   <li>blockUser=true 면 글쓴이를 함께 차단합니다(#36, 멱등). 차단은 별도 트랜잭션이라 차단 경쟁으로 신고가 실패하지 않습니다.</li>
 * </ul>
 * 글쓴이의 user_id와 본문은 응답·로그에 남기지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 신고 대상의 글쓴이와 숨김 처리 */
    private record Target(Long authorId, Runnable hide) {
    }

    private final ReportRepository reportRepository;
    private final WorrySheepRepository worrySheepRepository;
    private final WoolRepository woolRepository;
    private final UserRepository userRepository;
    private final WorrySheepAccess access;
    private final BlockService blockService;
    private final PolicyProperties policy;

    @Transactional
    public ReportResponse report(Long reporterId, ReportCreateRequest request) {
        if (request == null || request.targetType() == null || request.targetId() == null || request.reason() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        ReportTargetType type = request.targetType();
        Long targetId = request.targetId();

        // 잠금 조회가 이 트랜잭션의 첫 조회여야 합니다(위 설명 참고).
        Target target = switch (type) {
            case WORRY_SHEEP -> lockViewableSheep(reporterId, targetId);
            case WOOL -> lockReceivedWool(reporterId, targetId);
        };

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type, targetId)) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATED);
        }
        Report report;
        try {
            report = reportRepository.saveAndFlush(
                    new Report(userRepository.getReferenceById(reporterId), type, targetId, request.reason()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATED);
        }

        boolean blocked = false;
        if (request.blockUserOrFalse()) {
            // 차단은 BlockWriter 의 별도 트랜잭션에서 저장합니다. 같은 차단이 동시에 들어와 UNIQUE 위반이 나도
            // 그 트랜잭션만 롤백되고 기존 차단을 돌려주므로, 신고는 실패하지 않고 blocked 는 차단 행이 있으면 true 입니다.
            // 글쓴이 사용자 행을 잠그는 배너(activateCrisisBanner)보다 먼저 해야, 차단 행 저장의 외래 키 확인이
            // 이 트랜잭션의 행 잠금을 기다리지 않습니다(InnoDB).
            blockService.blockUser(reporterId, target.authorId());
            blocked = true;
        }

        if (request.reason() == ReportReason.LOOKS_DANGEROUS) {
            activateCrisisBanner(target.authorId());
        }

        boolean hidden = false;
        if (reportRepository.countByTargetTypeAndTargetId(type, targetId) >= policy.worry().autoHideReportCount()) {
            target.hide().run();
            hidden = true;
        }

        log.info("신고 접수: reportId={}, targetType={}, reason={}, hidden={}, blocked={}",
                report.getId(), type, request.reason(), hidden, blocked);
        return new ReportResponse(report.getId(), hidden, blocked);
    }

    /** 신고자가 볼 수 있는 양만 신고할 수 있습니다(상세와 같은 기준). 내 양은 400 입니다. */
    private Target lockViewableSheep(Long reporterId, Long sheepId) {
        WorrySheep sheep = worrySheepRepository.findByIdForUpdate(sheepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!access.canView(sheep, reporterId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (access.isOwner(sheep, reporterId)) {
            throw new BusinessException(ErrorCode.REPORT_OWN_CONTENT);
        }
        return new Target(sheep.getUser().getId(), sheep::hide);
    }

    /**
     * 받은 조언만 보이므로, 조언이 달린 양의 주인만 신고할 수 있습니다(숨긴 조언·숨김 양은 404).
     * 내가 보낸 조언이면 400 입니다.
     */
    private Target lockReceivedWool(Long reporterId, Long woolId) {
        Wool wool = woolRepository.findByIdForUpdate(woolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (wool.isHidden()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Long senderId = wool.getSender().getId();
        if (senderId.equals(reporterId)) {
            throw new BusinessException(ErrorCode.REPORT_OWN_CONTENT);
        }
        WorrySheep sheep = wool.getSheep();
        if (!access.isOwner(sheep, reporterId) || access.isHidden(sheep)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new Target(senderId, wool::hide);
    }

    /** 다른 기능이 같은 사용자 행(털실 등)을 동시에 바꿔도 덮어쓰지 않도록 잠근 뒤 배너를 켭니다. */
    private void activateCrisisBanner(Long authorId) {
        User author = userRepository.findByIdForUpdate(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        author.activateCrisisBanner(LocalDateTime.now().plusHours(policy.safety().crisisBannerHours()));
    }
}
