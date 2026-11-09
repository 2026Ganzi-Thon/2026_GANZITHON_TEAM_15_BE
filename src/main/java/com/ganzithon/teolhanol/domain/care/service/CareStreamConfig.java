package com.ganzithon.teolhanol.domain.care.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 케어 대화 SSE 처리를 요청 스레드와 분리해서 돌리는 실행기. LLM 응답을 기다리는 동안 스레드를 아끼려고 가상 스레드를 씁니다. */
@Configuration
class CareStreamConfig {

    @Bean(name = "careStreamExecutor", destroyMethod = "close")
    ExecutorService careStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
