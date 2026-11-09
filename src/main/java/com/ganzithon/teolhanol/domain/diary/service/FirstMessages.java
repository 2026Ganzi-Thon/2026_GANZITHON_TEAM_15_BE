package com.ganzithon.teolhanol.domain.diary.service;

import com.ganzithon.teolhanol.domain.diary.entity.Emotion;
import java.util.EnumMap;
import java.util.Map;

/** 감정별 AI 첫 마디 (LLM 호출 없는 고정 문장). 어조: 비난·명령 없는 다정한 말투 (기능명세서 6장). */
final class FirstMessages {

    private static final String ASK_CARE = "오늘은 어떤 돌봄이 필요해요?";

    private static final Map<Emotion, String> BY_EMOTION = new EnumMap<>(Emotion.class);

    static {
        BY_EMOTION.put(Emotion.JOY, "좋은 일이 있었나 봐요. 기분이 전해져요.");
        BY_EMOTION.put(Emotion.CALM, "마음이 편안하다니 다행이에요.");
        BY_EMOTION.put(Emotion.TIRED, "많이 지쳤구나.");
        BY_EMOTION.put(Emotion.ANXIOUS, "마음이 불안했구나. 혼자 안고 있느라 애썼어요.");
        BY_EMOTION.put(Emotion.DEPRESSED, "마음이 무거운 하루였구나.");
        BY_EMOTION.put(Emotion.LONELY, "외로운 마음이 들었구나. 여기서는 편하게 말해도 돼요.");
        BY_EMOTION.put(Emotion.ANGRY, "화가 나는 일이 있었구나. 그럴 만해요.");
        BY_EMOTION.put(Emotion.COMPLICATED, "마음이 한마디로 말하기 어렵게 복잡하구나.");
    }

    private FirstMessages() {
    }

    static String of(Emotion emotion) {
        return BY_EMOTION.get(emotion) + " " + ASK_CARE;
    }
}
