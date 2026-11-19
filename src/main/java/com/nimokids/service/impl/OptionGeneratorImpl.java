package com.nimokids.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.enums.QuestionType;
import com.nimokids.exception.InsufficientDistractorsException;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.OptionGenerator;
import com.nimokids.service.generation.DistractorRule;
import com.nimokids.service.generation.DynamicQuestion;
import com.nimokids.service.generation.DistractorRules;
import com.nimokids.service.generation.TagMatcher;
import com.nimokids.util.GameConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dynamic answer generation with explicit state matching (project_master_context.md, section 4).
 * Wrong answers come ONLY from positive tag matches ({@code metadata @> match}); the correct item and items already
 * picked are excluded by id, and names are kept distinct so a child never sees two identical words.
 */
@Service
@RequiredArgsConstructor
public class OptionGeneratorImpl implements OptionGenerator {

    /** Candidates fetched per wanted distractor, so duplicates by name can be skipped without a second query. */
    private static final int CANDIDATES_PER_PICK = 8;

    private final AnswerItemRepository answerItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    // A BusinessException here means "skip this question": the caller catches it and carries on, so it must not mark the
    // surrounding session transaction rollback-only (else the commit fails with UnexpectedRollbackException -> HTTP 500).
    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public List<SnapshotOption> generate(GameQuestion question, DynamicQuestion dynamic, boolean basicEnglishOnly) {
        AnswerItem correct = dynamic != null ? dynamic.correctItem() : question.getCorrectAnswerItem();
        if (correct == null) {
            throw new InvalidDistractorRulesException("Question " + question.getId() + " has no correct answer item and no generator");
        }
        // A generator that picks its own wrong answers (MATH_ARITHMETIC) does not need distractor_rules.
        boolean ownDistractors = dynamic != null && !dynamic.distractors().isEmpty();
        List<DistractorRule> rules = ownDistractors ? List.of()
                : DistractorRules.parse(question.getMetadata(), GameConstants.DISTRACTORS_PER_QUESTION);

        Map<String, Object> correctMatch = dynamic != null ? dynamic.correctMatch()
                : DistractorRules.parseCorrectMatch(question.getMetadata());
        if (!TagMatcher.satisfies(correct.getMetadata(), correctMatch)) {
            throw new InvalidDistractorRulesException("Question " + question.getId() + ": the correct answer '"
                    + correct.getName() + "' does not satisfy correct_match " + correctMatch);
        }

        Set<UUID> excludedIds = new LinkedHashSet<>();
        excludedIds.add(correct.getId());
        Set<String> usedNames = new HashSet<>();
        usedNames.add(normalized(correct.getName()));

        List<AnswerItem> distractors = new ArrayList<>();
        if (ownDistractors) {
            for (AnswerItem candidate : dynamic.distractors()) {
                // Same safety net as below: an item that could also be right is never shown as a wrong answer.
                if (TagMatcher.satisfies(candidate.getMetadata(), correctMatch)
                        || !usedNames.add(normalized(candidate.getName()))) {
                    throw new InvalidDistractorRulesException("Question " + question.getId() + ": generated wrong answer '"
                            + candidate.getName() + "' is not distinct from the correct answer " + correctMatch);
                }
                distractors.add(candidate);
                excludedIds.add(candidate.getId());
            }
        }
        for (DistractorRule rule : rules) {
            List<AnswerItem> candidates = answerItemRepository.findRandomMatching(
                    toJson(rule), List.copyOf(excludedIds), rule.count() * CANDIDATES_PER_PICK, basicEnglishOnly);
            int picked = 0;
            for (AnswerItem candidate : candidates) {
                if (picked == rule.count()) {
                    break;
                }
                // Safety net: an item that could also be right is never shown as a wrong answer.
                if (TagMatcher.satisfies(candidate.getMetadata(), correctMatch)) {
                    continue;
                }
                if (usedNames.add(normalized(candidate.getName()))) {
                    distractors.add(candidate);
                    excludedIds.add(candidate.getId());
                    picked++;
                }
            }
            if (picked < rule.count() && dynamic == null && canBorrowFromParent(question)) {
                // Q4: the topic's own category has too few words; borrow wrong answers from the PARENT topic's words.
                UUID parentId = question.getTopic().getParentTopic().getId();
                List<AnswerItem> borrowed = answerItemRepository.findRandomInTopicTree(
                        parentId, List.copyOf(excludedIds), (rule.count() - picked) * CANDIDATES_PER_PICK, basicEnglishOnly);
                for (AnswerItem candidate : borrowed) {
                    if (picked == rule.count()) {
                        break;
                    }
                    if (TagMatcher.satisfies(candidate.getMetadata(), correctMatch)) {
                        continue;
                    }
                    if (usedNames.add(normalized(candidate.getName()))) {
                        distractors.add(candidate);
                        excludedIds.add(candidate.getId());
                        picked++;
                    }
                }
            }
            if (picked < rule.count()) {
                throw new InsufficientDistractorsException("Question " + question.getId() + ": rule " + rule.match()
                        + " found " + picked + " usable items but needs " + rule.count());
            }
        }

        List<AnswerItem> all = new ArrayList<>(distractors);
        all.add(correct);
        Collections.shuffle(all, ThreadLocalRandom.current());
        preloadMedia(all);

        List<SnapshotOption> options = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            AnswerItem item = all.get(i);
            options.add(new SnapshotOption(
                    UUID.randomUUID(),
                    item.getId(),
                    item.getName(),
                    url(item.getImage()),
                    url(item.getVoice()),
                    i + 1,
                    item.getId().equals(correct.getId())));
        }
        return options;
    }

    /** Question types whose wrong answers may be any other word of the parent topic ("What is this?" style). */
    private static final Set<QuestionType> BORROWING_TYPES =
            Set.of(QuestionType.IMAGE_TO_WORD, QuestionType.SHAPE_RECOGNITION, QuestionType.COLOR_RECOGNITION);

    /**
     * Only picture-recognition questions borrow: for arithmetic, counting or attribute questions ("who can fly?") a word
     * of another group could be wrong in the wrong way (a shape as the answer of 2 + 3) or also be right.
     */
    private static boolean canBorrowFromParent(GameQuestion question) {
        return question.getQuestionType() != null && BORROWING_TYPES.contains(question.getQuestionType())
                && question.getTopic() != null && question.getTopic().getParentTopic() != null;
    }

    /**
     * Loads the image and voice of the 4 chosen items in ONE query, so building the options costs no lazy load per item.
     * Only the items that really reference a media asset are loaded: an item without image_id / voice_id has a null
     * association (no proxy), so reading it needs no query at all, and most items have none (pictures live in
     * metadata). Previously every candidate of every rule was re-read here, chosen or not.
     */
    private void preloadMedia(List<AnswerItem> items) {
        List<UUID> withMedia = items.stream()
                .filter(item -> item.getImage() != null || item.getVoice() != null)
                .map(AnswerItem::getId)
                .toList();
        if (!withMedia.isEmpty()) {
            answerItemRepository.findAllWithMedia(withMedia);
        }
    }

    private String toJson(DistractorRule rule) {
        try {
            return objectMapper.writeValueAsString(rule.match());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize distractor rule", ex);
        }
    }

    private static String normalized(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String url(MediaAsset asset) {
        return asset == null ? null : asset.getStorageUrl();
    }
}
