package com.ganzithon.teolhanol.domain.notification.push;

import java.io.IOException;
import java.security.GeneralSecurityException;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Notification;
import org.jose4j.lang.JoseException;

/**
 * 웹 푸시 요청(본문 RFC 8291 aes128gcm 암호화 + VAPID 서명 헤더)을 만듭니다. 암호화·서명은 web-push 라이브러리에 맡기고,
 * 전송은 타임아웃을 걸 수 있도록 {@link WebPushSender}가 JDK HttpClient로 합니다.
 */
class VapidRequestFactory extends AbstractPushService<VapidRequestFactory> {

    /** 키가 잘못되면 {@link GeneralSecurityException} */
    VapidRequestFactory(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
        super(publicKey, privateKey, subject);
    }

    HttpRequest request(Notification notification) throws GeneralSecurityException, IOException, JoseException {
        return prepareRequest(notification, Encoding.AES128GCM);
    }
}
