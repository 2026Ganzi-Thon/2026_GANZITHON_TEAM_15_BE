package com.ganzithon.teolhanol.domain.worry.repository;

import com.ganzithon.teolhanol.domain.worry.dto.FarmSheepRow;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.dto.MyWorryRow;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import java.util.List;
import java.util.Optional;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorrySheepRepository extends JpaRepository<WorrySheep, Long> {

    /** 상세 화면용: 작성자와 목장을 함께 가져옵니다. */
    @Query("select s from WorrySheep s join fetch s.user join fetch s.farm where s.id = :id")
    Optional<WorrySheep> findDetailById(@Param("id") Long id);

    /**
     * 주인이 상세를 확인한 시각만 기록합니다 (FRM-05). 잠금 없이 읽은 엔티티를 고쳐 저장하면 같은 순간의
     * 조언 보내기(털·졸업)·신고 숨김·자동 졸업 결과를 덮어쓸 수 있으므로, owner_checked_at 열만 갱신합니다.
     */
    @Modifying
    @Query("update WorrySheep s set s.ownerCheckedAt = :now where s.id = :id")
    int markOwnerChecked(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 털 개수·상태를 바꿀 때 쓰는 잠금 조회 (WOL-01). 양 행만 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorrySheep s where s.id = :id")
    Optional<WorrySheep> findByIdForUpdate(@Param("id") Long id);

    /**
     * 풀밭 양 목록 (FRM-01). 작성자 닉네임과 '이미 보냄' 여부를 한 쿼리로 가져오고, 정렬·페이지도 DB에서 합니다.
     * 제외: ACTIVE가 아닌 양, 내 양, 차단 관계(양방향) 사용자의 양, visibleRiskLevels에 없는 위험도의 양.
     * 정렬: 이미 보낸 양은 뒤로 → 털 적은 순 → 오래된 순 → id.
     * wools의 (sheep_id, sender_id) 유니크 덕분에 left join으로 행이 늘지 않습니다.
     *
     * @param farmId null이면 전체 목장
     */
    @Query(value = """
            select new com.ganzithon.teolhanol.domain.worry.dto.FarmSheepRow(
                w.id, f.code, w.woolCount, w.content, u.nickname, w.createdAt,
                case when mine.id is null then false else true end)
            from WorrySheep w
            join w.farm f
            join w.user u
            left join Wool mine on mine.sheep = w and mine.sender.id = :viewerId
            where w.status = com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus.ACTIVE
              and (:farmId is null or f.id = :farmId)
              and u.id <> :viewerId
              and w.riskLevel in :visibleRiskLevels
              and not exists (
                  select b.id from Block b
                  where (b.blocker.id = :viewerId and b.blocked.id = u.id)
                     or (b.blocker.id = u.id and b.blocked.id = :viewerId))
            order by case when mine.id is null then 0 else 1 end, w.woolCount, w.createdAt, w.id
            """)
    Slice<FarmSheepRow> findFarmSheep(@Param("viewerId") Long viewerId,
                                      @Param("farmId") Long farmId,
                                      @Param("visibleRiskLevels") Collection<RiskLevel> visibleRiskLevels,
                                      Pageable pageable);
    /**
     * 내 고민 목록 (FRM-05). 양마다 '확인 이후 새로 받은 털 수'를 한 번의 집계 쿼리로 함께 가져옵니다(N+1 없음).
     * owner_checked_at이 null이면 받은 털 전부가 새 털입니다. 신고 누적으로 숨겨진 조언(RPT-01)은 세지 않습니다. 최신순.
     */
    @Query("""
            select new com.ganzithon.teolhanol.domain.worry.dto.MyWorryRow(
                w.id, f.code, f.name, f.tabName, w.content, w.woolCount, w.status,
                w.createdAt, w.graduatedAt, count(nw.id))
            from WorrySheep w
            join w.farm f
            left join Wool nw on nw.sheep = w and nw.hidden = false
                and (w.ownerCheckedAt is null or nw.createdAt > w.ownerCheckedAt)
            where w.user.id = :userId and w.status in :statuses
            group by w.id, f.code, f.name, f.tabName, w.content, w.woolCount, w.status, w.createdAt, w.graduatedAt
            order by w.createdAt desc, w.id desc
            """)
    List<MyWorryRow> findMine(@Param("userId") Long userId,
                              @Param("statuses") Collection<WorrySheepStatus> statuses);

    /** 기준 시각 이후 이 사용자가 만든 고민 양 수. 숨김·졸업 포함 (하루 한도 검사용, FRM-04) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);

    /** 작성 시각이 threshold 이전(같은 시각 포함)인 ACTIVE 양을 한 번에 졸업시킵니다 (FRM-06). 숨김 양은 건드리지 않습니다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorrySheep w set w.status = com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus.GRADUATED,
                w.graduatedAt = :now
            where w.status = com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus.ACTIVE
              and w.createdAt <= :threshold
            """)
    int graduateCreatedBefore(@Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now);
}
