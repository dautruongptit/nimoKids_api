package com.nimokids.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.service.language.ResolvedQuestion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which picture the snapshot freezes: the question's own > the item's stored picture (id only) > the item's emoji. */
class QuestionSnapshotImageTest {

    private static final ResolvedQuestion RESOLVED =
            new ResolvedQuestion("What color is it?", null, null, List.of(), LanguageMode.EN, "en", "en");

    private static AnswerItem item(Map<String, Object> metadata) {
        AnswerItem item = AnswerItem.builder().code("COLORS_BLACK").name("Black").build();
        item.setId(UUID.randomUUID());
        item.getMetadata().putAll(metadata);
        return item;
    }

    private static GameQuestion question(AnswerItem correct, Map<String, Object> metadata) {
        return GameQuestion.builder().questionKey("TPL_WHAT_COLOR_COLORS_BLACK").correctAnswerItem(correct)
                .metadata(new HashMap<>(metadata)).build();
    }

    @Test
    void anItemWithAPictureIsShownThroughItsIdAndTheImageDataIsNotCopiedIntoTheSnapshot() {
        AnswerItem black = item(Map.of("emoji", "⚫", "image", "data:image/webp;base64,AAAA"));

        QuestionSnapshot snapshot = QuestionSnapshot.of(question(black, Map.of()), RESOLVED);

        assertThat(snapshot.questionImage()).isEqualTo("/api/v1/media/answer-items/" + black.getId() + "/image");
        assertThat(snapshot.questionImage()).doesNotContain("base64");
        assertThat(snapshot.correctAnswerItemId()).isEqualTo(black.getId());
    }

    @Test
    void anItemWithoutAPictureKeepsItsEmoji() {
        QuestionSnapshot snapshot = QuestionSnapshot.of(question(item(Map.of("emoji", "⚫")), Map.of()), RESOLVED);

        assertThat(snapshot.questionImage()).isEqualTo("⚫");
    }

    @Test
    void aBlankImageIsIgnoredAndTheEmojiIsUsed() {
        QuestionSnapshot snapshot =
                QuestionSnapshot.of(question(item(Map.of("emoji", "⚫", "image", "  ")), Map.of()), RESOLVED);

        assertThat(snapshot.questionImage()).isEqualTo("⚫");
    }

    @Test
    void theQuestionsOwnPictureStillWinsOverTheItemPicture() {
        AnswerItem two = item(Map.of("emoji", "2", "image", "data:image/webp;base64,AAAA"));

        QuestionSnapshot snapshot = QuestionSnapshot.of(question(two, Map.of("question_image", "7 − 5 = ?")), RESOLVED);

        assertThat(snapshot.questionImage()).isEqualTo("7 − 5 = ?");
    }
}
