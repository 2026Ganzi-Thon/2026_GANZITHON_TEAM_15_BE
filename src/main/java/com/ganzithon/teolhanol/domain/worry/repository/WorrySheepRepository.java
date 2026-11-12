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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorrySheepRepository extends JpaRepository<WorrySheep, Long> {

    /** 상세 화면용: 작성자와 목장을 함께 가져옵니다. */
    @Query("select s from WorrySheep s join fetch s.user join fetch s.farm where s.id = :id")
    Optional<WorrySheep> findDetailById(@Param("id") Long id);
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
     * owner_checked_at이 null이면 받은 털 전부가 새 털입니다. 최신순.
     */
    @Query("""
            select new com.ganzithon.teolhanol.domain.worry.dto.MyWorryRow(
                w.id, f.code, f.name, f.tabName, w.content, w.woolCount, w.status,
                w.createdAt, w.graduatedAt, count(nw.id))
            from WorrySheep w
            join w.farm f
            left join Wool nw on nw.sheep = w and (w.ownerCheckedAt is null or nw.createdAt > w.ownerCheckedAt)
            where w.user.id = :userId and w.status in :statuses
            group by w.id, f.code, f.name, f.tabName, w.content, w.woolCount, w.status, w.createdAt, w.graduatedAt
            order by w.createdAt desc, w.id desc
            """)
    List<MyWorryRow> findMine(@Param("userId") Long userId,
                              @Param("statuses") Collection<WorrySheepStatus> statuses);

    /** 기준 시각 이후 이 사용자가 만든 고민 양 수. 숨김·졸업 포함 (하루 한도 검사용, FRM-04) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
