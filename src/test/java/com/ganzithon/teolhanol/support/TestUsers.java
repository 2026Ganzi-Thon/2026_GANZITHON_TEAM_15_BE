package com.ganzithon.teolhanol.support;

import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.security.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인한 사용자가 필요한 API 테스트용 픽스처.
 *
 * <pre>
 * {@code
 * @SpringBootTest @AutoConfigureMockMvc @Import(TestUsers.class)
 * class SomeApiTest {
 *     @Autowired TestUsers testUsers;
 *     @Autowired MockMvc mockMvc;
 *
 *     @Test void 예시() throws Exception {
 *         TestUsers.Login me = testUsers.signIn("me@test.com", "내 닉네임");   // 회원 + 나의 양 생성
 *         mockMvc.perform(get("/some/api").header("Authorization", me.bearer()));
 *         // me.userId() 로 id 사용
 *     }
 * }
 * }
 * </pre>
 *
 * 테스트끼리 H2를 공유하므로 정리할 때는 my_sheep → users 순서로 지웁니다.
 */
@TestComponent
public class TestUsers {

    public static final String PASSWORD = "warmwool123";

    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtProvider jwtProvider;

    public Login signIn(String email, String nickname) {
        User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), nickname));
        mySheepRepository.saveAndFlush(new MySheep(user));
        return new Login(user.getId(), jwtProvider.issue(user.getId()));
    }

    public record Login(Long userId, String token) {
        /** Authorization 헤더 값 */
        public String bearer() {
            return "Bearer " + token;
        }
    }
}
