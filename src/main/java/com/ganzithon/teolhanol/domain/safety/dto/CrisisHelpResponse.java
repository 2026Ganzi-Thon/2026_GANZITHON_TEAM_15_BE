package com.ganzithon.teolhanol.domain.safety.dto;

import java.util.List;

/** 상담 연결 화면 정보 (SAF-02). 연락처와 문구는 {@code CrisisHelpInfo} 한 곳에서만 정합니다. */
public record CrisisHelpResponse(String title, String message, List<Contact> contacts,
                                 Emergency emergency, String hoursNote) {

    public record Contact(String name, String phone, String description) {
    }

    public record Emergency(String phone, String message) {
    }
}
