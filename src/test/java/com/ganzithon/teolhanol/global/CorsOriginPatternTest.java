package com.ganzithon.teolhanol.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.teolhanol.global.config.CorsProperties;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.cors.allowed-origins=https://teolhanol.vercel.app, https://*.vercel.app,http://localhost:*, ,")
@AutoConfigureMockMvc
class CorsOriginPatternTest {

    @Autowired
    MockMvc mockMvc;

    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(options("/diaries")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,accept"));
    }

    @Test
    void 패턴에_맞는_프리뷰_출처는_허용한다() throws Exception {
        String origin = "https://teolhanol-git-feat-x-team.vercel.app";
        preflight(origin)
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void 정확한_주소와_로컬_포트_패턴도_허용한다() throws Exception {
        preflight("https://teolhanol.vercel.app")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://teolhanol.vercel.app"));
        preflight("http://localhost:5173")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void 패턴에_맞지_않는_출처는_거절한다() throws Exception {
        preflight("https://evil.example.com")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        preflight("https://vercel.app.evil.com")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void 빈_값과_별표_단독은_무시한다() {
        assertThat(new CorsProperties(Arrays.asList(" ", "", "*", " http://a.com ")).originPatterns())
                .isEqualTo(List.of("http://a.com"));
        assertThat(new CorsProperties(List.of()).originPatterns()).isEmpty();
        assertThat(new CorsProperties(null).originPatterns()).isEmpty();
    }
}
