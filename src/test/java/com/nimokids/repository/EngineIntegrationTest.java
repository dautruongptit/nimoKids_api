package com.nimokids.repository;

import com.nimokids.util.GameConstants;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.config.TimeConfig;
import com.nimokids.dto.request.CreateGameSessionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import com.nimokids.dto.response.AnswerResponse;
import com.nimokids.dto.response.GameResultResponse;
import com.nimokids.dto.response.GameSessionResponse;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameMode;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.GameSession;
import com.nimokids.entity.SessionQuestion;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.Topic;
import com.nimokids.entity.enums.SessionStatus;
import com.nimokids.mapper.GameMapper;
import com.nimokids.service.GameSessionService;
import com.nimokids.service.OptionGenerator;
import com.nimokids.service.generation.DistractorRule;
import com.nimokids.service.generation.DistractorRules;
import com.nimokids.service.generation.TagMatcher;
import com.nimokids.service.impl.ActivityLogServiceImpl;
import com.nimokids.service.impl.DynamicQuestionServiceImpl;
import com.nimokids.service.impl.GameSessionServiceImpl;
import com.nimokids.service.impl.LanguageResolverServiceImpl;
import com.nimokids.service.impl.OptionGeneratorImpl;
import com.nimokids.service.impl.PlayerServiceImpl;
import com.nimokids.service.impl.StickerServiceImpl;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * The whole engine on a REAL PostgreSQL with the dev seed: the real option generator, the real services and the real
 * queries. Opt-in (DB_HOST). Everything is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GameSessionServiceImpl.class, PlayerServiceImpl.class, StickerServiceImpl.class, ActivityLogServiceImpl.class,
        OptionGeneratorImpl.class, DynamicQuestionServiceImpl.class, LanguageResolverServiceImpl.class, GameMapper.class,
        TimeConfig.class, JacksonAutoConfiguration.class})
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class EngineIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private GameSessionService gameSessionService;
    @Autowired private OptionGenerator optionGenerator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TopicRepository topicRepository;
    @Autowired private GameModeRepository gameModeRepository;
    @Autowired private GameQuestionRepository questionRepository;
    @Autowired private AnswerItemRepository answerItemRepository;
    @Autowired private GameSessionRepository sessionRepository;
    @Autowired private PlayerStickerRepository playerStickerRepository;

    // -------------------------------------------------------------------- seed
    //
    // The content is the vocabulary seeded by V16+ (topic tree, template questions, answer items). The tests derive their
    // expectations from the data instead of hard-coding counts, so adding a word does not break them.

    /** Every active question with a FIXED correct answer; dynamic ones (metadata.generator) decide theirs per session. */
    private List<GameQuestion> fixedAnswerQuestions() {
        return em.createQuery("select q from GameQuestion q join fetch q.correctAnswerItem join fetch q.topic "
                + "where q.active = true", GameQuestion.class).getResultList();
    }

    /** Distinct words a session could ask in these topics (P-10): AGE_1_3 questions, one per correct answer item. */
    private long playableWordsIn(List<UUID> topicIds) {
        return em.createQuery("select count(distinct coalesce(a.id, q.id)) from GameQuestion q left join q.correctAnswerItem a "
                + "where q.active = true and q.topic.id in :ids and (a is null or a.active = true) "
                + "and q.ageGroup = com.nimokids.entity.enums.AgeGroup.AGE_1_3", Long.class)
                .setParameter("ids", topicIds).getSingleResult();
    }

    @Test
    void aRootTopicIsPlayableExactlyWhenItsWholeSubtreeHasEnoughWords() {
        List<UUID> playable = questionRepository.findPlayableTopicIds(GameConstants.MIN_PLAYABLE_QUESTIONS_PER_TOPIC);
        List<Topic> roots = em.createQuery("select t from Topic t where t.parentTopic is null and t.active = true", Topic.class)
                .getResultList();
        assertThat(roots).isNotEmpty();
        for (Topic root : roots) {
            long words = playableWordsIn(topicRepository.findActiveSubtreeIds(root.getId()));
            assertThat(playable.contains(root.getId())).as(root.getCode() + " has " + words + " AGE_1_3 words in its subtree")
                    .isEqualTo(words >= GameConstants.MIN_PLAYABLE_QUESTIONS_PER_TOPIC);
        }
        // MATH and VEHICLES have no question of their own: they are playable only through their children.
        for (String parentOnly : List.of("MATH", "VEHICLES")) {
            Topic topic = topicRepository.findByCode(parentOnly).orElseThrow();
            assertThat(playableWordsIn(List.of(topic.getId()))).as(parentOnly).isZero();
            assertThat(playable).as(parentOnly).contains(topic.getId());
        }
    }

    @Test
    void aRootTopicDrawsQuestionsFromAllItsDescendants() {
        Topic animals = topicRepository.findByCode("ANIMALS").orElseThrow();
        List<UUID> children = List.of("FLY_ANIMALS", "LAND_ANIMALS", "SEA_ANIMALS").stream()
                .map(code -> topicRepository.findByCode(code).orElseThrow().getId()).toList();

        List<UUID> subtree = topicRepository.findActiveSubtreeIds(animals.getId());
        assertThat(subtree).contains(animals.getId()).containsAll(children);

        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();
        List<UUID> candidates = questionRepository.findCandidateIds(subtree, mode.getId(), new String[]{"AGE_1_3"});
        Set<UUID> candidateTopics = questionRepository.findAllById(candidates).stream()
                .map(question -> question.getTopic().getId()).collect(Collectors.toSet());
        assertThat(subtree).containsAll(candidateTopics);       // never a question from outside the subtree
        assertThat(candidateTopics).containsAll(children);      // and every child contributes
    }

    @Test
    void shortTemplateQuestionsAreGeneratedAndNeverOfferALookAlikeAsAWrongAnswer() {
        List<GameQuestion> generated = fixedAnswerQuestions().stream()
                .filter(question -> question.getTemplate() != null && "TEMPLATE".equals(question.getTemplate().getKind()))
                .toList();
        assertThat(generated.size()).as("generated template questions").isGreaterThanOrEqualTo(150);

        for (GameQuestion question : generated) {
            String label = question.getQuestionKey();
            assertThat(question.getQuestionText().length()).as(label + " is short").isLessThanOrEqualTo(25);
            Object lookalike = question.getCorrectAnswerItem().getMetadata().get("lookalike");
            for (int run = 0; run < 3; run++) {
                List<SnapshotOption> options = optionGenerator.generate(question);
                assertThat(options).as(label).hasSize(4);
                assertThat(options.stream().filter(SnapshotOption::isCorrect)).as(label).hasSize(1);
                if (lookalike != null) {
                    long sameLook = options.stream().map(option -> answerItemRepository.findById(option.answerItemId())
                            .orElseThrow().getMetadata().get("lookalike")).filter(lookalike::equals).count();
                    assertThat(sameLook).as(label + " offers a look-alike").isEqualTo(1);
                }
            }
        }
    }

    @Test
    void theOldStaticSeedWasReplacedAndNoLegacyRowsRemain() {
        Long legacyItems = (Long) em.createNativeQuery("select count(*) from answer_items where code like 'LEGACY%'").getSingleResult();
        Long staticTable = (Long) em.createNativeQuery("select count(*) from information_schema.tables where table_name = 'question_options'").getSingleResult();

        assertThat(legacyItems).isZero();
        assertThat(staticTable).isZero();
    }

    @Test
    void everyQuestionAlwaysProducesFourDistinctOptionsFromItsOwnPools() {
        List<GameQuestion> questions = fixedAnswerQuestions();
        assertThat(questions).isNotEmpty();

        for (GameQuestion question : questions) {
            String label = question.getQuestionText() + " -> " + question.getCorrectAnswerItem().getName();
            List<DistractorRule> rules = DistractorRules.parse(question.getMetadata(), 3);
            for (int run = 0; run < 3; run++) {
                List<SnapshotOption> options = optionGenerator.generate(question);

                assertThat(options).as(label).hasSize(4);
                assertThat(options.stream().map(SnapshotOption::text).collect(Collectors.toSet())).as(label).hasSize(4);
                assertThat(options.stream().filter(SnapshotOption::isCorrect)).as(label).hasSize(1);
                assertThat(options.stream().filter(SnapshotOption::isCorrect).findFirst().orElseThrow().answerItemId())
                        .as(label).isEqualTo(question.getCorrectAnswerItem().getId());
                // Every wrong answer comes from one of the question's own pools (positive tag match).
                for (SnapshotOption option : options.stream().filter(option -> !option.isCorrect()).toList()) {
                    Map<String, Object> tags = answerItemRepository.findById(option.answerItemId()).orElseThrow().getMetadata();
                    assertThat(rules.stream().anyMatch(rule -> TagMatcher.satisfies(tags, rule.match())))
                            .as(label + " / " + option.text()).isTrue();
                }
            }
        }
    }

    @Test
    void noSeededQuestionCanEverShowASecondRightAnswer() {
        List<AnswerItem> items = answerItemRepository.findAll();

        for (GameQuestion question : fixedAnswerQuestions()) {
            String label = question.getQuestionText() + " -> " + question.getCorrectAnswerItem().getName();
            Map<String, Object> correctMatch = DistractorRules.parseCorrectMatch(question.getMetadata());

            // The stated condition really describes the correct answer.
            assertThat(TagMatcher.satisfies(question.getCorrectAnswerItem().getMetadata(), correctMatch)).as(label).isTrue();

            // Every pool keeps enough SAFE wrong answers (items that do not also satisfy the condition) to fill its slots.
            for (DistractorRule rule : DistractorRules.parse(question.getMetadata(), 3)) {
                long safe = items.stream()
                        .filter(AnswerItem::isActive)
                        .filter(item -> !item.getId().equals(question.getCorrectAnswerItem().getId()))
                        .filter(item -> item.getMetadata().entrySet().containsAll(rule.match().entrySet()))
                        .filter(item -> !TagMatcher.satisfies(item.getMetadata(), correctMatch))
                        .count();
                assertThat(safe).as(label + " / safe wrong answers in pool " + rule.match()).isGreaterThanOrEqualTo(rule.count());
            }

            // Generated options: exactly one option satisfies the condition, and it is the correct one.
            for (int run = 0; run < 5; run++) {
                long satisfying = optionGenerator.generate(question).stream().filter(option -> TagMatcher.satisfies(
                        answerItemRepository.findById(option.answerItemId()).orElseThrow().getMetadata(), correctMatch)).count();
                assertThat(satisfying).as(label).isEqualTo(1);
            }
        }
    }

    @Test
    void lookAlikeAnswersAreNeverOfferedAsWrongAnswers() {
        List<AnswerItem> items = answerItemRepository.findAll().stream().filter(AnswerItem::isActive).toList();
        // Only questions where the risk is real: another active item in one of the pools shares the look-alike tag.
        List<GameQuestion> atRisk = fixedAnswerQuestions().stream().filter(question -> {
            Object look = question.getCorrectAnswerItem().getMetadata().get("lookalike");
            if (look == null) {
                return false;
            }
            List<DistractorRule> rules = DistractorRules.parse(question.getMetadata(), 3);
            return items.stream().anyMatch(item -> !item.getId().equals(question.getCorrectAnswerItem().getId())
                    && look.equals(item.getMetadata().get("lookalike"))
                    && rules.stream().anyMatch(rule -> TagMatcher.satisfies(item.getMetadata(), rule.match())));
        }).toList();
        assertThat(atRisk).as("questions whose pools contain a look-alike").isNotEmpty();

        for (GameQuestion question : atRisk) {
            Object look = question.getCorrectAnswerItem().getMetadata().get("lookalike");
            for (int run = 0; run < 10; run++) {
                List<SnapshotOption> wrong = optionGenerator.generate(question).stream().filter(o -> !o.isCorrect()).toList();
                for (SnapshotOption option : wrong) {
                    assertThat(answerItemRepository.findById(option.answerItemId()).orElseThrow().getMetadata().get("lookalike"))
                            .as(question.getQuestionKey() + " offered the look-alike " + option.text()).isNotEqualTo(look);
                }
            }
        }
    }

    // ------------------------------------------------------------- full engine

    @Test
    void aWholeGameOnTheSeedPlaysThroughTheRealServices() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Topic animals = topicRepository.findByCode("ANIMALS").orElseThrow();
        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();

        GameSessionResponse started = gameSessionService.createSession(
                anonymousId, new CreateGameSessionRequest(animals.getId(), mode.getId(), null));
        em.flush();
        em.clear();

        // The payload sent to the child carries 4 options and no correctness information at all.
        assertThat(started.question().options()).hasSize(4);
        assertThat(objectMapper.writeValueAsString(started)).doesNotContainIgnoringCase("correct");
        assertThat(started.timeLimitSeconds()).isEqualTo(8);

        GameSession session = sessionRepository.findBySessionId(started.sessionId()).orElseThrow();
        List<SessionQuestion> questions = session.getSessionQuestions();
        assertThat(questions).hasSize(5);

        // The subtree of a root always includes the root itself.
        Set<UUID> allowedTopics = new HashSet<>(topicRepository.findActiveSubtreeIds(animals.getId()));
        Set<UUID> questionIds = new HashSet<>();
        for (SessionQuestion sq : questions) {
            assertThat(allowedTopics).contains(sq.getQuestion().getTopic().getId());
            questionIds.add(sq.getQuestion().getId());
            assertThat(sq.getOptionsSnapshot()).hasSize(4);
            assertThat(sq.getOptionsSnapshot().stream().filter(SnapshotOption::isCorrect)).hasSize(1);
        }
        assertThat(questionIds).hasSize(5);

        // Play: right answers for the first 3 questions, wrong ones for the last 2. The server grades from the snapshot.
        AnswerResponse last = null;
        for (int i = 0; i < 5; i++) {
            SessionQuestion sq = questions.get(i);
            SnapshotOption pick = sq.getOptionsSnapshot().stream()
                    .filter(option -> option.isCorrect() == (questions.indexOf(sq) < 3))
                    .findFirst().orElseThrow();
            last = gameSessionService.submitAnswer(anonymousId, session.getSessionId(),
                    new SubmitAnswerRequest(sq.getQuestion().getId(), pick.optionId()));

            assertThat(last.correct()).isEqualTo(i < 3);
            assertThat(last.correctAnswer().id()).isEqualTo(
                    sq.getOptionsSnapshot().stream().filter(SnapshotOption::isCorrect).findFirst().orElseThrow().optionId());
            if (i < 4) {
                assertThat(last.nextQuestion().question().id()).isEqualTo(questions.get(i + 1).getQuestion().getId());
                assertThat(objectMapper.writeValueAsString(last.nextQuestion())).doesNotContainIgnoringCase("correct");
            } else {
                assertThat(last.nextQuestion()).isNull();
            }
        }
        em.flush();
        em.clear();

        GameResultResponse result = gameSessionService.getResult(anonymousId, session.getSessionId());
        assertThat(result.score()).isEqualTo(3);
        assertThat(result.correctAnswers()).isEqualTo(3);
        assertThat(result.wrongAnswers()).isEqualTo(2);
        assertThat(result.accuracy()).isEqualTo(60.0);
        assertThat(result.maxStreak()).isEqualTo(3);
        assertThat(result.earnedStickers()).extracting(s -> s.code()).contains("FIRST_GAME");

        GameSession finished = sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        // The evidence is permanent: the stored snapshot still matches what the child was shown.
        assertThat(finished.getSessionQuestions().get(0).getOptionsSnapshot())
                .extracting(SnapshotOption::optionId)
                .containsExactlyInAnyOrderElementsOf(
                        questions.get(0).getOptionsSnapshot().stream().map(SnapshotOption::optionId).toList());
        assertThat(finished.getSessionQuestions().get(0).getSelectedOptionId()).isNotNull();
        assertThat(Map.of("fly", playerStickerRepository.findBySessionId(finished.getId()).size())).containsEntry("fly", 1);
    }

    @Test
    void aLeafTopicOnlyAsksItsOwnQuestions() {
        UUID anonymousId = UUID.randomUUID();
        Topic fruits = topicRepository.findByCode("FRUITS").orElseThrow();
        GameMode mode = gameModeRepository.findByCode("GUESS").orElseThrow();

        GameSessionResponse started = gameSessionService.createSession(
                anonymousId, new CreateGameSessionRequest(fruits.getId(), mode.getId(), null));
        em.flush();
        em.clear();

        GameSession session = sessionRepository.findBySessionId(started.sessionId()).orElseThrow();
        assertThat(session.getSessionQuestions()).allSatisfy(
                sq -> assertThat(sq.getQuestion().getTopic().getCode()).isEqualTo("FRUITS"));
    }
}
