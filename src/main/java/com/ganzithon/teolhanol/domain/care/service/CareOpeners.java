package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.diary.entity.CareType;
import java.util.EnumMap;
import java.util.Map;

/** LLM을 쓸 수 없을 때 건네는 케어 방식별 첫 메시지 */
final class CareOpeners {

    private static final Map<CareType, String> FALLBACK = new EnumMap<>(CareType.class);

    static {
        FALLBACK.put(CareType.LISTEN, "편하게 이야기해 주세요. 판단하지 않고 끝까지 들을게요. 지금 마음에 가장 크게 남아 있는 건 뭐예요?");
        FALLBACK.put(CareType.COMFORT, "오늘도 여기까지 오느라 애썼어요. 그 마음, 충분히 그럴 만해요. 어떤 일이 마음에 남아 있어요?");
        FALLBACK.put(CareType.ORGANIZE, "같이 천천히 생각을 정리해 봐요. 지금 가장 마음에 걸리는 게 무엇인지 하나만 말해 줄래요?");
        FALLBACK.put(CareType.SOLVE, "작은 것부터 같이 찾아봐요. 먼저, 어떤 상황이었는지 편하게 들려줄래요?");
    }

    private CareOpeners() {
    }

    static String fallback(CareType careType) {
        return FALLBACK.get(careType);
    }
}
