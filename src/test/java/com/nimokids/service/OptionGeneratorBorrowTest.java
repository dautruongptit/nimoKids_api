package com.nimokids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.QuestionType;
import com.nimokids.exception.InsufficientDistractorsException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.impl.OptionGeneratorImpl;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Q4 (BUSINESS_RULES.md 4.4): a topic whose own category is too small borrows wrong answers from its parent topic. */
class OptionGeneratorBorrowTest {

    private final AnswerItemRepository repository = mock(AnswerItemRepository.class);
    private final OptionGenerator generator = new OptionGeneratorImpl(repository, new ObjectMapper());
    private final Topic parent = topic("VEHICLES", null);
    private final Topic child = topic("FLY_VEHICLES", parent);

    private static Topic topic(String code, Topic parent) {
        Topic topic = Topic.builder().code(code).name(code).parentTopic(parent).build();
        topic.setId(UUID.randomUUID());
        return topic;
    }

    private static AnswerItem item(String name) {
        AnswerItem item = AnswerItem.builder().code(name.toUpperCase()).name(name).build();
        item.setId(UUID.randomUUID());
        return item;
    }

    private GameQuestion question(QuestionType type, Topic topic) {
        AnswerItem plane = item("Plane");
        plane.getMetadata().put("category", "fly_vehicles");
        GameQuestion question = GameQuestion.builder().questionText("What is this?").questionType(type).topic(topic)
                .correctAnswerItem(plane)
                .metadata(Map.of("correct_match", Map.of("category", "fly_vehicles"),
                        "distractor_rules", List.of(Map.of("match", Map.of("category", "fly_vehicles"), "count", 3)))).build();
        question.setId(UUID.randomUUID());
        return question;
    }

    @Test
    void aTooSmallCategoryBorrowsTheMissingWrongAnswersFromTheParentTopic() {
        when(repository.findRandomMatching(anyString(), any(), anyInt(), anyBoolean())).thenReturn(List.of(item("Kite"))); // only 1 of 3
        when(repository.findRandomInTopicTree(eq(parent.getId()), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of(item("Car"), item("Boat"), item("Bus")));

        List<SnapshotOption> options = generator.generate(question(QuestionType.IMAGE_TO_WORD, child));

        assertThat(options).hasSize(4);
        assertThat(options).extracting(SnapshotOption::text).containsExactlyInAnyOrder("Plane", "Kite", "Car", "Boat");
        assertThat(options.stream().filter(SnapshotOption::isCorrect)).hasSize(1);
    }

    @Test
    void nothingIsBorrowedWhenTheOwnCategoryHasEnoughWords() {
        when(repository.findRandomMatching(anyString(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of(item("Kite"), item("Rocket"), item("Balloon")));

        generator.generate(question(QuestionType.IMAGE_TO_WORD, child));

        verify(repository, never()).findRandomInTopicTree(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void arithmeticAndAttributeQuestionsNeverBorrow() {
        when(repository.findRandomMatching(anyString(), any(), anyInt(), anyBoolean())).thenReturn(List.of(item("Kite")));

        for (QuestionType type : List.of(QuestionType.SIMPLE_ADDITION, QuestionType.ATTRIBUTE_MATCHING)) {
            assertThatThrownBy(() -> generator.generate(question(type, child))).isInstanceOf(InsufficientDistractorsException.class);
        }
        verify(repository, never()).findRandomInTopicTree(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void aRootTopicHasNoParentToBorrowFrom() {
        when(repository.findRandomMatching(anyString(), any(), anyInt(), anyBoolean())).thenReturn(List.of(item("Kite")));

        assertThatThrownBy(() -> generator.generate(question(QuestionType.IMAGE_TO_WORD, parent)))
                .isInstanceOf(InsufficientDistractorsException.class);
        verify(repository, never()).findRandomInTopicTree(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void stillFailsWhenTheParentCannotHelpEither() {
        when(repository.findRandomMatching(anyString(), any(), anyInt(), anyBoolean())).thenReturn(List.of(item("Kite")));
        when(repository.findRandomInTopicTree(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of(item("Car")));

        assertThatThrownBy(() -> generator.generate(question(QuestionType.IMAGE_TO_WORD, child)))
                .isInstanceOf(InsufficientDistractorsException.class);
    }
}
