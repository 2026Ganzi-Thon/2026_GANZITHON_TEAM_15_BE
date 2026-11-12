package com.ganzithon.teolhanol.domain.safety.dto;

import java.util.Locale;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 조언 다시 확인에서 걸린 이유 (SAF-04). 사용자에게는 이 분류와 안내 문구만 보여 주고, 어떤 단어가 걸렸는지는 알려 주지 않습니다.
 * BANNED_WORD는 1차 금칙어(BLOCKED), 나머지는 2차 LLM 어조 분류(SUGGEST)입니다.
 */
@Getter
@RequiredArgsConstructor
public enum AdviceCategory {

    BANNED_WORD("금칙어", false, "마음을 다치게 할 수 있는 표현이 들어 있어요. 조금 다르게 써 볼까요?"),
    BLAME("비난", true, "탓하는 말처럼 들릴 수 있어요."),
    LECTURE("다그침·훈계", true, "다그치는 말처럼 들릴 수 있어요."),
    COMPARE("비교", true, "다른 사람과 견주는 말처럼 들릴 수 있어요."),
    MOCK("조롱", true, "놀리는 말처럼 들릴 수 있어요."),
    DIAGNOSIS("섣부른 진단", true, "마음 상태를 단정하는 말처럼 들릴 수 있어요."),
    SOLICIT("종교·상품 권유", true, "무언가를 권하는 말처럼 들릴 수 있어요.");

    private final String label;
    /** LLM 어조 분류 결과로 나올 수 있는지 */
    private final boolean tone;
    /** 다시 확인 화면에 보여 줄 안내 문구 */
    private final String message;

    /** LLM이 돌려준 분류 코드를 어조 분류로 바꿉니다. NONE이거나 모르는 값이면 비어 있습니다. */
    public static Optional<AdviceCategory> ofTone(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String cleaned = code.strip().toUpperCase(Locale.ROOT);
        for (AdviceCategory category : values()) {
            if (category.tone && category.name().equals(cleaned)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
