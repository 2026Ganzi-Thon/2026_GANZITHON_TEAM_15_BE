package com.ganzithon.teolhanol.domain.sheep.service;

import com.ganzithon.teolhanol.domain.sheep.dto.SheepItemsRequest;
import com.ganzithon.teolhanol.domain.sheep.dto.SheepItemsResponse;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheepItem;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepItemRepository;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.exception.BusinessException;
import com.ganzithon.teolhanol.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 털실로 나의 양 꾸미기 (SHP-03). 꾸미기는 모양만 바꾸고 털 단계에는 영향을 주지 않습니다.
 * 만들기(털실 차감·아이템 지급)와 착용 저장은 한 트랜잭션이며, 털실이 모자라면 아무것도 바뀌지 않습니다.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SheepItemProperties.class)
public class SheepItemService {

    private final SheepItemProperties catalog;
    private final UserRepository userRepository;
    private final MySheepRepository mySheepRepository;
    private final MySheepItemRepository mySheepItemRepository;

    @Transactional(readOnly = true)
    public SheepItemsResponse get(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        MySheep sheep = mySheepRepository.findByUserId(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return response(user, ownedBy(sheep), 0);
    }

    @Transactional
    public SheepItemsResponse save(Long userId, SheepItemsRequest request) {
        // 털실이 동시에 두 번 차감되지 않도록 사용자와 나의 양을 잠근다
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        MySheep sheep = mySheepRepository.findByUserIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Map<String, MySheepItem> owned = ownedBy(sheep);

        int spent = 0;
        for (String code : distinctKnown(request.craft())) {
            if (owned.containsKey(code)) {
                continue;
            }
            int cost = costOf(code);
            if (!user.spendYarn(cost)) {
                throw new BusinessException(ErrorCode.YARN_NOT_ENOUGH);
            }
            spent += cost;
            owned.put(code, mySheepItemRepository.save(new MySheepItem(sheep, code)));
        }

        if (request.equipped() != null) {
            Set<String> wanted = distinctKnown(request.equipped());
            for (String code : wanted) {
                if (!owned.containsKey(code)) {
                    throw new BusinessException(ErrorCode.ITEM_NOT_OWNED);
                }
            }
            owned.forEach((code, item) -> item.setEquipped(wanted.contains(code)));
        }
        return response(user, owned, spent);
    }

    private Map<String, MySheepItem> ownedBy(MySheep sheep) {
        Map<String, MySheepItem> owned = new LinkedHashMap<>();
        mySheepItemRepository.findByMySheepIdOrderById(sheep.getId()).forEach(i -> owned.put(i.getItemCode(), i));
        return owned;
    }

    /** 요청의 코드를 중복 없이 모으며, 목록에 없는 코드는 400입니다. */
    private Set<String> distinctKnown(List<String> codes) {
        Set<String> result = new LinkedHashSet<>();
        if (codes == null) {
            return result;
        }
        for (String code : codes) {
            if (code == null || catalog.items().stream().noneMatch(i -> i.code().equals(code))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 아이템이에요.");
            }
            result.add(code);
        }
        return result;
    }

    private int costOf(String code) {
        return catalog.items().stream().filter(i -> i.code().equals(code)).findFirst().orElseThrow().yarnCost();
    }

    private SheepItemsResponse response(User user, Map<String, MySheepItem> owned, int spent) {
        List<SheepItemsResponse.Item> items = catalog.items().stream().map(i -> {
            MySheepItem mine = owned.get(i.code());
            return new SheepItemsResponse.Item(i.code(), i.name(), i.yarnCost(), mine != null, mine != null && mine.isEquipped());
        }).toList();
        return new SheepItemsResponse(user.getYarnCount(), spent, items);
    }
}
