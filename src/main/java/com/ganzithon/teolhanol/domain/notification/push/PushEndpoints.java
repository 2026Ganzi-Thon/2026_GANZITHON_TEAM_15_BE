package com.ganzithon.teolhanol.domain.notification.push;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** 구독 endpoint 검사 도우미. 서버가 이 주소로 요청을 보내므로 https와 알려진 푸시 서비스 호스트만 받습니다. */
public final class PushEndpoints {

    private PushEndpoints() {
    }

    /** https이고 호스트가 허용 목록(그 자체 또는 하위 도메인)에 있으면 true */
    public static boolean isAllowed(String endpoint, List<String> allowedHosts) {
        URI uri = parse(endpoint);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return allowedHosts.stream()
                .map(h -> h.strip().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty())
                .anyMatch(h -> host.equals(h) || host.endsWith("." + h));
    }

    /** 로그용 호스트. endpoint 전체(구독 토큰 포함)는 로그에 남기지 않습니다. */
    public static String host(String endpoint) {
        URI uri = parse(endpoint);
        return uri == null || uri.getHost() == null ? "unknown" : uri.getHost();
    }

    private static URI parse(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        try {
            return URI.create(endpoint.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
