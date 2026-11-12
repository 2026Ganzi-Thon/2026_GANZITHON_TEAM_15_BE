package com.ganzithon.teolhanol.domain.notification.config;

import com.ganzithon.teolhanol.domain.notification.push.PushSender;
import com.ganzithon.teolhanol.domain.notification.push.WebPushSetup;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 알림(WOL-02) 설정: 웹 푸시 발송기와, 요청 스레드를 막지 않도록 알림을 보내는 작은 전용 스레드풀. */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class NotificationConfig {

    public static final String EXECUTOR = "notificationExecutor";

    @Bean
    public WebPushSetup webPushSetup(PushProperties properties) {
        return WebPushSetup.from(properties);
    }

    @Bean
    public PushSender pushSender(WebPushSetup setup) {
        return setup.sender();
    }

    /** 몰리면 큐가 찰 때까지 쌓고, 넘치면 그 알림은 버립니다(요청 스레드를 붙잡지 않음). */
    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
