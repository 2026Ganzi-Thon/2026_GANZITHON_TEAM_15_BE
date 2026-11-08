package com.ganzithon.teolhanol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.teolhanol.domain.care.entity.CareMessage;
import com.ganzithon.teolhanol.domain.care.entity.MessageRole;
import com.ganzithon.teolhanol.domain.care.repository.CareMessageRepository;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import jakarta.persistence.EntityManager;
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
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    @Test
    void 일기와_대화_본문은_DB에_암호문으로_저장되고_조회하면_평문이다() {
        User user = userRepository.save(new User("crypto01", "hash", "다정한 목동 #0001"));
        Diary diary = diaryRepository.save(new Diary(user, Emotion.TIRED, 4, "오늘 회사에서 많이 지쳤어요", RiskLevel.SAFE));
        careMessageRepository.save(new CareMessage(diary, MessageRole.USER, "그냥 들어줬으면 해요"));
        entityManager.flush();
        entityManager.clear();

        String rawDiary = jdbcTemplate.queryForObject("select content from diaries where id = ?", String.class, diary.getId());
        String rawMessage = jdbcTemplate.queryForObject("select content from care_messages where diary_id = ?", String.class, diary.getId());

        assertThat(rawDiary).doesNotContain("지쳤어요");
        assertThat(rawMessage).doesNotContain("들어줬으면");
        assertThat(diaryRepository.findById(diary.getId()).orElseThrow().getContent())
                .isEqualTo("오늘 회사에서 많이 지쳤어요");
    }
}
