package com.ganzithon.teolhanol.domain.farm.repository;

import com.ganzithon.teolhanol.domain.farm.dto.FarmResponse;
import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    boolean existsByCode(String code);

    /** 목장 전체와 목장별 ACTIVE 양 수를 한 번의 쿼리로 가져옵니다. */
    @Query("""
            select new com.ganzithon.teolhanol.domain.farm.dto.FarmResponse(
                f.code, f.name, f.tabName, f.description, count(w.id))
            from Farm f
            left join WorrySheep w on w.farm = f and w.status = com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus.ACTIVE
            group by f.id, f.code, f.name, f.tabName, f.description, f.sortOrder
            order by f.sortOrder
            """)
    List<FarmResponse> findAllWithWaitingCount();

    Optional<Farm> findByCode(String code);
}
