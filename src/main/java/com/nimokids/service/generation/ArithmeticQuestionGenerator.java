package com.nimokids.service.generation;

import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.util.GameConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Generator {@code MATH_ARITHMETIC}: a very easy sum or difference ("5 + 2 =") that a pre-school child can work out in
 * the 5-10 seconds of the countdown. The rule lives in {@code game_questions.metadata.generator}:
 *
 * <pre>
 * { "type": "MATH_ARITHMETIC", "operators": ["+", "-"], "minOperand": 1, "maxOperand": 5, "maxResult": 10,
 *   "allowNegative": false, "optionCount": 4, "easyPairs": false, "numberCategory": "arithmetic_number" }
 * </pre>
 *
 * {@code easyPairs} (ages 4-5) lets the round pairs {@code 5 + 5}, {@code 10 - 5} and {@code 10 + 2} appear too, when the
 * operators and {@code maxResult} allow them. The time limit is the question row's {@code time_limit_seconds}.
 *
 * Nothing is stored per expression: it is drawn for every session. The correct answer and the three wrong answers are
 * rows of {@code answer_items} (category {@code numberCategory}, tag {@code value}), so their text and voice come from the
 * database like every other option. The expression travels as {@link DynamicQuestion#questionImage()} and is frozen in
 * the session snapshot together with the options.
 */
public class ArithmeticQuestionGenerator {

    public static final String TYPE = "MATH_ARITHMETIC";
    public static final String DEFAULT_NUMBER_CATEGORY = "arithmetic_number";
    private static final int LIMIT_RESULT = 20;
    private static final int MAX_PLAIN_ATTEMPTS = 20;
    /** The subtrahend stays tiny (1-3) unless it is the minuend itself or its half: easy to do on the fingers. */
    private static final int EASY_SUBTRAHEND = 3;
    private static final char MINUS_SIGN = '−';
    /** Round pairs that are easy to do in the head: {minuend or first addend, operator, second operand}. */
    private static final List<int[]> ROUND_PAIRS = List.of(new int[] {5, '+', 5}, new int[] {10, '-', 5}, new int[] {10, '+', 2});

    private final AnswerItemRepository answerItemRepository;
    private final RandomGenerator random;

    public ArithmeticQuestionGenerator(AnswerItemRepository answerItemRepository, RandomGenerator random) {
        this.answerItemRepository = answerItemRepository;
        this.random = random;
    }

    /** One drawn expression. {@code operator} is '+' or '-'. */
    public record Problem(int a, char operator, int b, int result) {
        /** "5 + 2 =" (a real minus sign for subtraction: "7 - 2 ="). */
        public String expression() {
            return a + " " + (operator == '-' ? MINUS_SIGN : '+') + " " + b + " =";
        }
    }

    /** The validated rule. */
    record Rule(List<Character> operators, int minOperand, int maxOperand, int maxResult, boolean easyPairs,
                String numberCategory) {
    }

    public DynamicQuestion generate(GameQuestion question, Map<?, ?> generator, boolean basicEnglishOnly) {
        Rule rule = parse(generator, question);
        Problem problem = draw(rule);

        // The numbers shown as answers: the result plus 3 different ones from 0..maxResult, all rows of the answer vocabulary.
        List<Integer> others = new ArrayList<>();
        for (int value = 0; value <= rule.maxResult(); value++) {
            if (value != problem.result()) {
                others.add(value);
            }
        }
        shuffle(others);
        List<Integer> wanted = new ArrayList<>(others.subList(0, GameConstants.DISTRACTORS_PER_QUESTION));
        wanted.add(problem.result());

        Map<Integer, AnswerItem> byValue = new HashMap<>();
        for (AnswerItem item : answerItemRepository.findActiveByCategoryAndValues(
                rule.numberCategory(), wanted.stream().map(String::valueOf).toList(), basicEnglishOnly)) {
            if (item.getMetadata() != null && item.getMetadata().get("value") instanceof Number number) {
                byValue.putIfAbsent(number.intValue(), item);
            }
        }
        for (int value : wanted) {
            if (!byValue.containsKey(value)) {
                throw new InvalidDistractorRulesException("Question " + question.getId() + ": no active number item with value "
                        + value + " in category '" + rule.numberCategory() + "'");
            }
        }
        List<AnswerItem> distractors = wanted.subList(0, GameConstants.DISTRACTORS_PER_QUESTION).stream()
                .map(byValue::get).toList();

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("generator", TYPE);
        audit.put("expression", problem.expression());
        audit.put("a", problem.a());
        audit.put("operator", String.valueOf(problem.operator()));
        audit.put("b", problem.b());
        audit.put("result", problem.result());
        return new DynamicQuestion(byValue.get(problem.result()), Map.of("value", problem.result()), null, audit,
                problem.expression(), distractors);
    }

    /** The drawing algorithm alone (no database): the result is never negative and never above {@code maxResult}. */
    Problem draw(Rule rule) {
        if (rule.easyPairs() && random.nextInt(3) == 0) {
            List<int[]> allowed = ROUND_PAIRS.stream()
                    .filter(pair -> rule.operators().contains((char) pair[1]))
                    .filter(pair -> resultOf(pair[0], (char) pair[1], pair[2]) <= rule.maxResult())
                    .toList();
            if (!allowed.isEmpty()) {
                int[] pair = allowed.get(random.nextInt(allowed.size()));
                return new Problem(pair[0], (char) pair[1], pair[2], resultOf(pair[0], (char) pair[1], pair[2]));
            }
        }
        char operator = rule.operators().get(random.nextInt(rule.operators().size()));
        return operator == '+' ? sum(rule) : difference(rule);
    }

    private Problem sum(Rule rule) {
        for (int attempt = 0; attempt < MAX_PLAIN_ATTEMPTS; attempt++) {
            int a = random.nextInt(rule.minOperand(), rule.maxOperand() + 1);
            int b = random.nextInt(rule.minOperand(), rule.maxOperand() + 1);
            if (a + b <= rule.maxResult()) {
                return new Problem(a, '+', b, a + b);
            }
        }
        // parse() guarantees 2 * minOperand <= maxResult, so the smallest pair always fits.
        return new Problem(rule.minOperand(), '+', rule.minOperand(), 2 * rule.minOperand());
    }

    private Problem difference(Rule rule) {
        int a = random.nextInt(rule.minOperand(), rule.maxOperand() + 1);
        int b = random.nextInt(rule.minOperand(), rule.maxOperand() + 1);
        if (a < b) {                                    // never a negative result: swap
            int swapped = a;
            a = b;
            b = swapped;
        }
        boolean easy = b <= EASY_SUBTRAHEND || b == a || 2 * b == a;
        if (!easy) {                                    // too hard to do in 5-10 s: take 1..3 away instead
            int lowest = Math.max(rule.minOperand(), 1);
            int highest = Math.min(EASY_SUBTRAHEND, a);
            b = highest >= lowest ? random.nextInt(lowest, highest + 1) : a;
        }
        return new Problem(a, '-', b, a - b);
    }

    private static int resultOf(int a, char operator, int b) {
        return operator == '+' ? a + b : a - b;
    }

    /** Fisher-Yates with the injected generator (Collections.shuffle needs a java.util.Random). */
    private void shuffle(List<Integer> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer held = values.get(i);
            values.set(i, values.get(j));
            values.set(j, held);
        }
    }

    private static Rule parse(Map<?, ?> generator, GameQuestion question) {
        String where = "Question " + question.getId() + ": generator " + TYPE + " ";
        List<Character> operators = new ArrayList<>();
        if (generator.get("operators") instanceof List<?> list) {
            for (Object entry : list) {
                String symbol = String.valueOf(entry);
                if (!symbol.equals("+") && !symbol.equals("-")) {
                    throw new InvalidDistractorRulesException(where + "operators may only be \"+\" and \"-\", got " + symbol);
                }
                if (!operators.contains(symbol.charAt(0))) {
                    operators.add(symbol.charAt(0));
                }
            }
        }
        if (operators.isEmpty()) {
            throw new InvalidDistractorRulesException(where + "needs a non-empty operators list");
        }
        int min = intOf(generator.get("minOperand"), 1);
        int max = intOf(generator.get("maxOperand"), 5);
        int maxResult = intOf(generator.get("maxResult"), 10);
        if (min < 0 || max < min || maxResult < GameConstants.DISTRACTORS_PER_QUESTION || maxResult > LIMIT_RESULT) {
            throw new InvalidDistractorRulesException(where + "needs 0 <= minOperand <= maxOperand and "
                    + GameConstants.DISTRACTORS_PER_QUESTION + " <= maxResult <= " + LIMIT_RESULT
                    + " (got " + min + ".." + max + ", maxResult " + maxResult + ")");
        }
        if (operators.contains('+') && 2 * min > maxResult) {
            throw new InvalidDistractorRulesException(where + "minOperand " + min + " leaves no sum within maxResult " + maxResult);
        }
        if (Boolean.TRUE.equals(generator.get("allowNegative"))) {
            throw new InvalidDistractorRulesException(where + "allowNegative must be false: there are no negative number items");
        }
        int optionCount = intOf(generator.get("optionCount"), GameConstants.OPTIONS_PER_QUESTION);
        if (optionCount != GameConstants.OPTIONS_PER_QUESTION) {
            throw new InvalidDistractorRulesException(where + "optionCount must be " + GameConstants.OPTIONS_PER_QUESTION);
        }
        String category = generator.get("numberCategory") instanceof String text && !text.isBlank()
                ? text : DEFAULT_NUMBER_CATEGORY;
        return new Rule(List.copyOf(operators), min, max, maxResult, Boolean.TRUE.equals(generator.get("easyPairs")), category);
    }

    private static int intOf(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }
}
