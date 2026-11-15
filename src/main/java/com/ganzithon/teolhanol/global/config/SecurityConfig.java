package com.ganzithon.teolhanol.global.config;

import com.ganzithon.teolhanol.global.exception.ErrorCode;
import com.ganzithon.teolhanol.global.response.ApiResponse;
import java.util.List;
import com.ganzithon.teolhanol.global.security.JwtAuthenticationFilter;
import com.ganzithon.teolhanol.global.security.JwtProvider;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 설정. 공개 경로를 뺀 모든 API는 Bearer JWT가 필요합니다 (ACC-02).
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    /** 로그인 없이 접근할 수 있는 경로 (기능명세서 2장: 비회원은 서비스 소개, 위기 상담 안내만) */
    public static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/help/crisis",
            "/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error"
    };

    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE(케어 대화·알림 스트림)가 끝날 때의 비동기 디스패치. 처음 요청에서 이미 인증을 거쳤고,
                        // JWT 필터는 이 디스패치에서 다시 돌지 않으므로 여기서 막으면 이미 보낸 응답에 401을 쓰려다 오류가 납니다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, ErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res, ErrorCode code) throws java.io.IOException {
        res.setStatus(code.getStatus().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), ApiResponse.fail(code.getCode(), code.getMessage()));
    }
}
