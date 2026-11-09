package com.ganzithon.teolhanol.domain.user.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 닉네임을 쓸 수 없는 이유. 화면에는 종류와 문구만 알려주고 어떤 단어에 걸렸는지는 알려주지 않습니다. */
@Getter
@RequiredArgsConstructor
public enum NicknameReason {

    LENGTH("닉네임은 %d~%d자로 정해 주세요."),
    CHARACTERS("한글, 영문, 숫자와 띄어쓰기 한 칸만 쓸 수 있어요."),
    DUPLICATE("이미 다른 목동이 쓰고 있는 이름이에요."),
    NOT_ALLOWED("이 이름은 쓸 수 없어요. 다른 이름을 골라 주세요.");

    private final String messageTemplate;
}
