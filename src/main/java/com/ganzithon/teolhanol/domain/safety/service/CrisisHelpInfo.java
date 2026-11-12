package com.ganzithon.teolhanol.domain.safety.service;

import com.ganzithon.teolhanol.domain.safety.dto.CrisisHelpResponse;
import com.ganzithon.teolhanol.domain.safety.dto.CrisisHelpResponse.Contact;
import com.ganzithon.teolhanol.domain.safety.dto.CrisisHelpResponse.Emergency;
import java.util.List;

/**
 * 상담 연결 정보의 단일 출처 (SAF-02).
 * 상담 연결 화면, 고민 올리기 crisis 응답(#23), 대화 속 전환(#13)이 같은 값을 쓰도록 여기서 가져갑니다.
 */
public final class CrisisHelpInfo {

    public static final String SUICIDE_PREVENTION_PHONE = "109";
    public static final String MENTAL_HEALTH_PHONE = "1577-0199";
    public static final String EMERGENCY_PHONE = "119";
    public static final String HOURS_NOTE = "두 곳 모두 24시간";

    public static final CrisisHelpResponse INFO = new CrisisHelpResponse(
            "전화로 이야기해 볼래요?",
            "지금 마음이 많이 힘드시다면, 이야기를 들어 줄 사람이 있어요. 편한 곳에 연락해 보세요.",
            List.of(
                    new Contact("자살예방 상담전화", SUICIDE_PREVENTION_PHONE,
                            "힘든 마음을 언제든 이야기할 수 있어요."),
                    new Contact("정신건강 위기상담", MENTAL_HEALTH_PHONE,
                            "마음이 많이 힘들 때 전문 상담사와 이야기할 수 있어요.")),
            new Emergency(EMERGENCY_PHONE, "지금 위험하다면 119에 바로 연락해 주세요."),
            HOURS_NOTE);

    private CrisisHelpInfo() {
    }
}
