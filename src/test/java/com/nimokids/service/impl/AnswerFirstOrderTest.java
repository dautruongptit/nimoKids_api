package com.nimokids.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** BUSINESS_RULES.md 4.1: the answer word is picked first, so a word with many wordings is not drawn more often. */
class AnswerFirstOrderTest {

    private final UUID bear = UUID.randomUUID();
    private final UUID cat = UUID.randomUUID();

    @Test
    void everyQuestionAppearsExactlyOnce() {
        Map<UUID, UUID> wordOf = new HashMap<>();
        List<UUID> candidates = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID q = UUID.randomUUID();
            candidates.add(q);
            wordOf.put(q, bear);
        }
        UUID generated = UUID.randomUUID();
        candidates.add(generated);

        List<UUID> ordered = GameSessionServiceImpl.answerFirstOrder(candidates, wordOf::get);

        assertThat(ordered).containsExactlyInAnyOrderElementsOf(candidates);
    }

    @Test
    void aWordWithManyWordingsIsNotDrawnMoreOftenThanAWordWithOne() {
        Map<UUID, UUID> wordOf = new HashMap<>();
        List<UUID> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {              // 5 wordings of "bear"
            UUID q = UUID.randomUUID();
            candidates.add(q);
            wordOf.put(q, bear);
        }
        UUID catQuestion = UUID.randomUUID();       // 1 wording of "cat"
        candidates.add(catQuestion);
        wordOf.put(catQuestion, cat);

        int catFirst = 0;
        int runs = 4000;
        for (int run = 0; run < runs; run++) {
            if (GameSessionServiceImpl.answerFirstOrder(candidates, wordOf::get).get(0).equals(catQuestion)) {
                catFirst++;
            }
        }
        // By question the cat would open 1/6 of the sessions (~17 %); by word it opens half of them.
        assertThat(catFirst / (double) runs).isBetween(0.44, 0.56);
    }

    @Test
    void theFirstQuestionsAreOnePerWordBeforeAnySecondWording() {
        Map<UUID, UUID> wordOf = new HashMap<>();
        List<UUID> candidates = new ArrayList<>();
        for (int word = 0; word < 3; word++) {
            UUID w = UUID.randomUUID();
            for (int i = 0; i < 3; i++) {
                UUID q = UUID.randomUUID();
                candidates.add(q);
                wordOf.put(q, w);
            }
        }

        List<UUID> ordered = GameSessionServiceImpl.answerFirstOrder(candidates, wordOf::get);

        assertThat(ordered.subList(0, 3).stream().map(wordOf::get).distinct()).hasSize(3);
    }

    @Test
    void interleaveFollowsTheAgeMixPattern() {
        List<UUID> hard = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<UUID> easy = List.of(UUID.randomUUID(), UUID.randomUUID());

        // HEHEH
        assertThat(GameSessionServiceImpl.interleave(hard, easy))
                .containsExactly(hard.get(0), easy.get(0), hard.get(1), easy.get(1), hard.get(2));
    }
}
