package com.ganzithon.teolhanol.global.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ganzithon.teolhanol.global.llm.LlmConfig.Provider;
import org.junit.jupiter.api.Test;

class LlmConfigTest {

    @Test
    void auto는_Upstage_키가_있으면_Upstage_없으면_Claude_둘_다_없으면_꺼짐() {
        assertThat(LlmConfig.chooseProvider("auto", true, true)).isEqualTo(Provider.UPSTAGE);
        assertThat(LlmConfig.chooseProvider("auto", false, true)).isEqualTo(Provider.CLAUDE);
        assertThat(LlmConfig.chooseProvider("auto", false, false)).isEqualTo(Provider.NONE);
        assertThat(LlmConfig.chooseProvider("", true, false)).isEqualTo(Provider.UPSTAGE);
        assertThat(LlmConfig.chooseProvider(null, false, true)).isEqualTo(Provider.CLAUDE);
    }

    @Test
    void 직접_지정하면_그_공급자의_키가_있을_때만_켜진다() {
        assertThat(LlmConfig.chooseProvider("claude", true, true)).isEqualTo(Provider.CLAUDE);
        assertThat(LlmConfig.chooseProvider("claude", true, false)).isEqualTo(Provider.NONE);
        assertThat(LlmConfig.chooseProvider("UPSTAGE", true, true)).isEqualTo(Provider.UPSTAGE);
        assertThat(LlmConfig.chooseProvider("upstage", false, true)).isEqualTo(Provider.NONE);
    }

    @Test
    void 알_수_없는_공급자는_시작을_막는다() {
        assertThatThrownBy(() -> LlmConfig.chooseProvider("gpt", true, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Upstage_기본_주소와_모델() {
        UpstageProperties p = new UpstageProperties("k", " ", null);
        assertThat(p.baseUrl()).isEqualTo("https://api.upstage.ai/v1");
        assertThat(p.model()).isEqualTo("solar-pro4");
        assertThat(p.enabled()).isTrue();
        assertThat(new UpstageProperties("", null, null).enabled()).isFalse();
    }
}
