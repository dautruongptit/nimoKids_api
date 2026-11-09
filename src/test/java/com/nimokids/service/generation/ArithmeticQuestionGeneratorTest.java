package com.nimokids.service.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.generation.ArithmeticQuestionGenerator.Problem;
import com.nimokids.service.generation.ArithmeticQuestionGenerator.Rule;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArithmeticQuestionGeneratorTest {

    private final AnswerItemRepository repository = mock(AnswerItemRepository.class);

    private ArithmeticQuestionGenerator generator(long seed) {
        return new ArithmeticQuestionGenerator(repository, new Random(seed));
    }

    private static AnswerItem number(int value) {
        AnswerItem item = AnswerItem.builder().code("ARITH_" + value).name(String.valueOf(value)).build();
        item.setId(UUID.randomUUID());
        item.getMetadata().putAll(Map.of("category", "arithmetic_number", "value", value));
        return item;
    }

    private static GameQuestion question() {
        GameQuestion question = GameQuestion.builder().questionText("What is the result?").build();
        question.setId(UUID.randomUUID());
        return question;
    }

    /** The database holds number items 0..20. */
    private void stubNumbers() {
        when(repository.findActiveByCategoryAndValues(eq("arithmetic_number"), anyCollection(), anyBoolean()))
                .thenAnswer(invocation -> invocation.<Collection<String>>getArgument(1).stream()
                        .map(Integer::parseInt).filter(value -> value >= 0 && value <= 20).map(ArithmeticQuestionGeneratorTest::number).toList());
    }

    private static Map<String, Object> rule() {
        return Map.of("type", "MATH_ARITHMETIC", "operators", List.of("+", "-"), "minOperand", 1, "maxOperand", 5,
                "maxResult", 10, "allowNegative", false, "optionCount", 4);
    }

    @Test
    void everyDrawnExpressionIsEasyAndNeverNegative() {
        ArithmeticQuestionGenerator generator = generator(7);
        Rule rule = new Rule(List.of('+', '-'), 1, 5, 10, false, "arithmetic_number");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            Problem problem = generator.draw(rule);
            seen.add(String.valueOf(problem.operator()));
            assertThat(problem.result()).isBetween(0, 10);
            assertThat(problem.a()).isBetween(1, 5);
            assertThat(problem.b()).isBetween(1, 5);
            if (problem.operator() == '+') {
                assertThat(problem.result()).isEqualTo(problem.a() + problem.b());
            } else {
                assertThat(problem.result()).isEqualTo(problem.a() - problem.b());
                assertThat(problem.b() <= 3 || problem.b() == problem.a() || 2 * problem.b() == problem.a())
                        .as("subtrahend of %s is easy", problem.expression()).isTrue();
            }
        }
        assertThat(seen).containsExactlyInAnyOrder("+", "-");
    }

    @Test
    void additionKeepsTheSumWithinMaxResult() {
        ArithmeticQuestionGenerator generator = generator(3);
        Rule rule = new Rule(List.of('+'), 3, 5, 7, false, "arithmetic_number");   // 3+3, 3+4, 4+3 only
        for (int i = 0; i < 2000; i++) {
            assertThat(generator.draw(rule).result()).isBetween(6, 7);
        }
    }

    @Test
    void easyPairsAddRoundNumbersOnlyWhenTheRuleAllowsThem() {
        Rule wide = new Rule(List.of('+', '-'), 1, 5, 20, true, "arithmetic_number");
        Set<String> expressions = new HashSet<>();
        ArithmeticQuestionGenerator generator = generator(11);
        for (int i = 0; i < 3000; i++) {
            expressions.add(generator.draw(wide).expression());
        }
        assertThat(expressions).contains("5 + 5 =", "10 − 5 =", "10 + 2 =");

        Rule narrow = new Rule(List.of('+', '-'), 1, 5, 10, true, "arithmetic_number");   // 10 + 2 would be 12 > 10
        ArithmeticQuestionGenerator again = generator(11);
        for (int i = 0; i < 3000; i++) {
            assertThat(again.draw(narrow).result()).isBetween(0, 10);
        }
    }

    @Test
    void generateBuildsFourDistinctNumberOptionsAndTheExpression() {
        stubNumbers();
        ArithmeticQuestionGenerator generator = generator(5);
        for (int i = 0; i < 300; i++) {
            DynamicQuestion dynamic = generator.generate(question(), rule(), false);

            assertThat(dynamic.questionImage()).matches("\\d+ [+−] \\d+ =");
            int result = ((Number) dynamic.correctItem().getMetadata().get("value")).intValue();
            assertThat(dynamic.correctMatch()).isEqualTo(Map.of("value", result));
            assertThat(dynamic.audit()).containsEntry("result", result).containsEntry("expression", dynamic.questionImage());
            assertThat(dynamic.visual()).isNull();

            assertThat(dynamic.distractors()).hasSize(3);
            Set<Integer> values = new HashSet<>();
            values.add(result);
            for (AnswerItem wrong : dynamic.distractors()) {
                int value = ((Number) wrong.getMetadata().get("value")).intValue();
                assertThat(value).isBetween(0, 10).isNotEqualTo(result);
                assertThat(values.add(value)).as("distinct wrong answers").isTrue();
            }
        }
    }

    @Test
    void theCorrectNumberMatchesTheShownExpression() {
        stubNumbers();
        DynamicQuestion dynamic = generator(9).generate(question(), rule(), false);
        String[] parts = dynamic.questionImage().split(" ");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[2]);
        int expected = parts[1].equals("+") ? a + b : a - b;
        assertThat(dynamic.correctItem().getName()).isEqualTo(String.valueOf(expected));
    }

    @Test
    void aMissingNumberItemSkipsTheQuestionInsteadOfShowingFewerOptions() {
        when(repository.findActiveByCategoryAndValues(eq("arithmetic_number"), anyCollection(), anyBoolean()))
                .thenReturn(List.of(number(1)));
        assertThatThrownBy(() -> generator(1).generate(question(), rule(), false))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("no active number item");
    }

    @Test
    void aMalformedRuleIsRejected() {
        ArithmeticQuestionGenerator generator = generator(1);
        assertThatThrownBy(() -> generator.generate(question(), Map.of("operators", List.of("*")), false))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("operators");
        assertThatThrownBy(() -> generator.generate(question(), Map.of("operators", List.of()), false))
                .isInstanceOf(InvalidDistractorRulesException.class);
        assertThatThrownBy(() -> generator.generate(question(),
                Map.of("operators", List.of("+"), "allowNegative", true), false))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("allowNegative");
        assertThatThrownBy(() -> generator.generate(question(),
                Map.of("operators", List.of("+"), "optionCount", 3), false))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("optionCount");
        assertThatThrownBy(() -> generator.generate(question(),
                Map.of("operators", List.of("+"), "minOperand", 6, "maxOperand", 8, "maxResult", 10), false))
                .isInstanceOf(InvalidDistractorRulesException.class).hasMessageContaining("maxResult");
    }
}
