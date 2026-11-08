package com.ganzithon.teolhanol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EncryptionPersistenceTest {

    @Autowired UserRepository userRepository;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareMessageRepository careMessageRepository;
    @Autowired CareSummaryRepository careSummaryRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    @Test
    void 대화와_요약_본문은_DB에_암호문으로_저장되고_조회하면_평문이다() {
        User user = userRepository.save(new User("crypto01@test.com", "hash", "다정한 목동"));
        Diary diary = diaryRepository.save(new Diary(user, Emotion.TIRED));
        careMessageRepository.save(new CareMessage(diary, MessageRole.USER, "오늘 회사에서 많이 지쳤어요"));
        careSummaryRepository.save(new CareSummary(diary, "그냥 들어줬으면 해요", LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        String rawMessage = jdbcTemplate.queryForObject("select content from care_messages where diary_id = ?", String.class, diary.getId());
        String rawSummary = jdbcTemplate.queryForObject("select summary from care_summaries where diary_id = ?", String.class, diary.getId());

        assertThat(rawMessage).doesNotContain("지쳤어요");
        assertThat(rawSummary).doesNotContain("들어줬으면");
        assertThat(careMessageRepository.findAll().get(0).getContent()).isEqualTo("오늘 회사에서 많이 지쳤어요");
    }
}
