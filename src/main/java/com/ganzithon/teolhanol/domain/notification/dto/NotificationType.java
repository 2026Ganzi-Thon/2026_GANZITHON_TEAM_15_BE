package com.ganzithon.teolhanol.domain.notification.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 알림 종류. 알림 문구에는 조언 내용·닉네임을 넣지 않습니다. */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    /** 내 고민 양에 털(조언)이 도착함 → 양 주인 */
    WOOL_ARRIVED("wool-arrived", "털이 풍성해졌어요", "내 고민 양에 새 털 한 올이 도착했어요."),
    /** 내가 보낸 조언에 고마워요가 눌림 → 조언 보낸 사람 */
    WOOL_THANKED("wool-thanked", "고마워요가 도착했어요", "보내 주신 털 한 올에 고마운 마음이 전해졌어요.");

    /** SSE 이벤트 이름 */
    private final String eventName;
    private final String title;
    private final String body;
}
