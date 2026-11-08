package com.ganzithon.teolhanol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ganzithon.teolhanol.domain.care.entity.CareSummary;
import com.ganzithon.teolhanol.domain.care.repository.CareSummaryRepository;
import com.ganzithon.teolhanol.domain.diary.entity.CareStatus;
import com.ganzithon.teolhanol.domain.diary.entity.Diary;
import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import com.ganzithon.teolhanol.domain.diary.repository.DiaryRepository;
import com.ganzithon.teolhanol.domain.farm.entity.Farm;
import com.ganzithon.teolhanol.domain.farm.repository.FarmRepository;
import com.ganzithon.teolhanol.domain.sheep.entity.MySheep;
import com.ganzithon.teolhanol.domain.sheep.repository.MySheepRepository;
import com.ganzithon.teolhanol.domain.user.entity.User;
import com.ganzithon.teolhanol.domain.user.repository.UserRepository;
import com.ganzithon.teolhanol.domain.wool.entity.Wool;
import com.ganzithon.teolhanol.domain.wool.repository.WoolRepository;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheep;
import com.ganzithon.teolhanol.domain.worry.entity.WorrySheepStatus;
import com.ganzithon.teolhanol.domain.worry.repository.WorrySheepRepository;
import com.ganzithon.teolhanol.global.config.JpaConfig;
import com.ganzithon.teolhanol.global.enums.RiskLevel;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaConfig.class)
class EntityMappingTest {

    @Autowired UserRepository userRepository;
    @Autowired MySheepRepository mySheepRepository;
    @Autowired DiaryRepository diaryRepository;
    @Autowired CareSummaryRepository careSummaryRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired WorrySheepRepository worrySheepRepository;
    @Autowired WoolRepository woolRepository;

    private User user(String loginId) {
        return userRepository.saveAndFlush(new User(loginId, "hash", "다정한 목동 #" + loginId));
    }

    @Test
    void 회원과_나의_양을_저장하면_기본값과_생성시각이_채워진다() {
        User user = user("1001");
        MySheep sheep = mySheepRepository.saveAndFlush(new MySheep(user));

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getYarnCount()).isZero();
        assertThat(sheep.getStage()).isEqualTo(1);
        assertThat(sheep.getWoolCount()).isZero();
    }

    @Test
    void 나의_양은_회원당_한_마리만_만들_수_있다() {
        User user = user("1002");
        mySheepRepository.saveAndFlush(new MySheep(user));

        assertThatThrownBy(() -> mySheepRepository.saveAndFlush(new MySheep(user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 위기로_판정된_일기는_케어가_중단된_상태로_저장된다() {
        User user = user("1003");
        Diary safe = diaryRepository.saveAndFlush(new Diary(user, Emotion.CALM, 2, "평온한 하루", RiskLevel.SAFE));
        Diary crisis = diaryRepository.saveAndFlush(new Diary(user, Emotion.DEPRESSED, 5, "힘든 하루", RiskLevel.CRISIS));

        assertThat(safe.getCareStatus()).isEqualTo(CareStatus.NONE);
        assertThat(crisis.getCareStatus()).isEqualTo(CareStatus.STOPPED);
    }

    @Test
    void 요약_카드는_일기당_하나만_저장된다() {
        Diary diary = diaryRepository.saveAndFlush(
                new Diary(user("1004"), Emotion.ANXIOUS, 3, "불안한 하루", RiskLevel.SAFE));
        careSummaryRepository.saveAndFlush(new CareSummary(diary, "요약", null, LocalDateTime.now()));

        assertThatThrownBy(() -> careSummaryRepository.saveAndFlush(
                new CareSummary(diary, "요약2", null, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_양에_같은_사람이_조언을_두_번_보낼_수_없다() {
        User owner = user("1005");
        User sender = user("1006");
        Farm farm = farmRepository.saveAndFlush(new Farm("CAREER", "취업·진로", "설명", 1));
        WorrySheep sheep = worrySheepRepository.saveAndFlush(new WorrySheep(owner, farm, "고민", RiskLevel.SAFE));

        assertThat(sheep.getStatus()).isEqualTo(WorrySheepStatus.ACTIVE);
        assertThat(sheep.getStage()).isEqualTo(1);

        woolRepository.saveAndFlush(new Wool(sheep, sender, "응원해요"));
        assertThatThrownBy(() -> woolRepository.saveAndFlush(new Wool(sheep, sender, "또 응원해요")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 위험도는_더_높은_등급을_고른다() {
        assertThat(RiskLevel.max(RiskLevel.SAFE, RiskLevel.CAUTION)).isEqualTo(RiskLevel.CAUTION);
        assertThat(RiskLevel.max(RiskLevel.CRISIS, RiskLevel.CAUTION)).isEqualTo(RiskLevel.CRISIS);
        assertThat(RiskLevel.max(null, RiskLevel.SAFE)).isEqualTo(RiskLevel.SAFE);
    }
}
