package com.nimokids.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimokids.dto.response.QuestionResponse;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.QuestionSnapshot;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The screen draws a COLOR_RECOGNITION picture as a plain colour, so the frozen question type must reach the response. */
class GameMapperQuestionTypeTest {

    private final GameMapper mapper = new GameMapper();

    private static SessionQuestion sessionQuestion(String questionType) {
        GameQuestion question = GameQuestion.builder().questionText("What color is it?").build();
        question.setId(UUID.randomUUID());
        QuestionSnapshot snapshot = new QuestionSnapshot("TPL_WHAT_COLOR_COLORS_RED", questionType, "AGE_1_3", 1, null,
                "COLORS", "What color is it?", null, null, null, "/api/v1/media/answer-items/x/image", UUID.randomUUID(),
                "EN", Map.of(), "WHAT_COLOR", "en", "en", null);
        return SessionQuestion.builder().question(question).questionNumber((short) 1).questionSnapshot(snapshot)
                .optionsSnapshot(List.of(new SnapshotOption(UUID.randomUUID(), UUID.randomUUID(), "Red", null, null, 1, true)))
                .build();
    }

    @Test
    void theFrozenQuestionTypeIsExposedToTheScreen() {
        QuestionResponse response = mapper.toQuestionResponse(sessionQuestion("COLOR_RECOGNITION"));

        assertThat(response.questionType()).isEqualTo("COLOR_RECOGNITION");
        assertThat(response.questionImage()).isEqualTo("/api/v1/media/answer-items/x/image");
    }

    @Test
    void aSnapshotWithoutATypeGivesNullAndNothingBreaks() {
        assertThat(mapper.toQuestionResponse(sessionQuestion(null)).questionType()).isNull();
    }
}
