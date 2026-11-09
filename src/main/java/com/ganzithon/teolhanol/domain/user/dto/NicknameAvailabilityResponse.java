package com.ganzithon.teolhanol.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ganzithon.teolhanol.domain.user.service.NicknameReason;

/** 닉네임 사용 가능 여부. 쓸 수 없으면 이유 종류와 안내 문구를 함께 줍니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NicknameAvailabilityResponse(boolean available, NicknameReason reason, String message) {

    public static NicknameAvailabilityResponse ok() {
        return new NicknameAvailabilityResponse(true, null, "사용할 수 있어요.");
    }
}
