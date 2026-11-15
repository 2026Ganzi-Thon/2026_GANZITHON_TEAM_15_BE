package com.ganzithon.teolhanol.domain.notification.controller;

import com.ganzithon.teolhanol.domain.notification.dto.PushSubscribeRequest;
import com.ganzithon.teolhanol.domain.notification.dto.PushSubscriptionResponse;
import com.ganzithon.teolhanol.domain.notification.dto.PushUnsubscribeRequest;
import com.ganzithon.teolhanol.domain.notification.dto.VapidPublicKeyResponse;
import com.ganzithon.teolhanol.domain.notification.push.WebPushSetup;
import com.ganzithon.teolhanol.domain.notification.service.PushSubscriptionService;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import com.ganzithon.teolhanol.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "털 도착 알림 (웹 푸시 + 앱 안)")
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService subscriptionService;
    private final WebPushSetup webPush;

    @Operation(summary = "VAPID 공개키 (WOL-02)",
            description = "pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: publicKey })에 쓸 공개키입니다. "
                    + "서버에 VAPID 키가 없으면 enabled=false, publicKey=null이며 이때는 앱 안 알림(SSE)만 씁니다.")
    @GetMapping("/vapid-public-key")
    public ApiResponse<VapidPublicKeyResponse> publicKey(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(new VapidPublicKeyResponse(webPush.publicKey(), webPush.enabled()));
    }

    @Operation(summary = "웹 푸시 구독 저장 (WOL-02)",
            description = "브라우저 PushSubscription.toJSON() 결과의 endpoint와 keys(p256dh, auth)를 보냅니다. "
                    + "새 구독은 201, 같은 endpoint가 이미 있으면 소유자를 지금 로그인한 사용자로 바꾸고 200입니다(멱등). "
                    + "endpoint는 https의 알려진 푸시 서비스 주소만 받습니다(아니면 400 PUSH_ENDPOINT_NOT_ALLOWED). "
                    + "웹 푸시가 꺼져 있어도 저장은 되며 pushEnabled로 알려 줍니다.")
    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<PushSubscriptionResponse>> subscribe(@AuthenticationPrincipal AuthUser user,
                                                                           @Valid @RequestBody PushSubscribeRequest request) {
        PushSubscriptionService.Result result = subscriptionService.subscribe(user.id(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(new PushSubscriptionResponse(true, webPush.enabled())));
    }

    @Operation(summary = "웹 푸시 구독 해지 (WOL-02)",
            description = "이 브라우저의 구독(endpoint)을 지웁니다. 로그아웃할 때 부르면 다른 계정 알림이 오지 않습니다. "
                    + "본인 구독만 지우며, 없거나 다른 사람 구독이어도 200(subscribed=false)입니다.")
    @DeleteMapping("/subscriptions")
    public ApiResponse<PushSubscriptionResponse> unsubscribe(@AuthenticationPrincipal AuthUser user,
                                                             @Valid @RequestBody PushUnsubscribeRequest request) {
        subscriptionService.unsubscribe(user.id(), request.endpoint());
        return ApiResponse.ok(new PushSubscriptionResponse(false, webPush.enabled()));
    }
}
