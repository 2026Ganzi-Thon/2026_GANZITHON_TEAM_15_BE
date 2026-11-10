package com.ganzithon.teolhanol.domain.user.service;

import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.user.dto.RecordsDeleteResponse;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.user.service.erase.UserDataEraser;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기록 전체 삭제와 탈퇴 (ACC-03). 모든 삭제는 한 트랜잭션이라 중간에 실패하면 아무것도 지워지지 않습니다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** @Order 순서대로 주입됩니다 */
    private final List<UserDataEraser> erasers;
    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;

    @PersistenceContext
    private EntityManager em;

    /** 마음 돌보기 기록(오늘의 마음·대화·마무리 카드)만 지웁니다. 나의 양 털과 털실, 아이템은 유지합니다. */
    @Transactional
    public RecordsDeleteResponse deleteRecords(Long userId) {
        requireUser(userId);
        long count = diaryRepository.countByUserId(userId);
        erasers.forEach(e -> e.eraseRecords(userId));
        return new RecordsDeleteResponse(count);
    }

    /** 탈퇴: 대화, 나의 양, 고민 양, 보낸 조언, 신고·차단 등 이 사용자의 모든 데이터를 지우고 마지막에 계정을 지웁니다. */
    @Transactional
    public void deleteAccount(Long userId) {
        requireUser(userId);
        erasers.forEach(e -> e.eraseAll(userId));
        // 벌크 삭제는 영속성 컨텍스트를 거치지 않으므로 비우고 나서 사용자 행을 지운다
        em.flush();
        em.clear();
        userRepository.deleteById(userId);
        log.info("회원 탈퇴 처리 userId={}", userId);
    }

    private void requireUser(Long userId) {
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
